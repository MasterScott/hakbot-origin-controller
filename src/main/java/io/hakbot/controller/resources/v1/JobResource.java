/*
 * This file is part of Hakbot Origin Controller.
 *
 * Hakbot Origin Controller is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Hakbot Origin Controller is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Hakbot Origin Controller. If not, see http://www.gnu.org/licenses/.
 */
package io.hakbot.controller.resources.v1;

import alpine.Config;
import alpine.event.framework.EventService;
import alpine.model.ApiKey;
import io.hakbot.HakbotConfigKey;
import io.hakbot.controller.event.JobUpdateEvent;
import io.hakbot.controller.model.Job;
import io.hakbot.controller.model.JobArtifact;
import io.hakbot.controller.persistence.QueryManager;
import io.hakbot.controller.workers.State;
import io.hakbot.util.JsonUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.Principal;
import java.util.List;

@Path("/v1/job")
@Api(value = "job", authorizations = {
        @Authorization(value = "X-Api-Key")
})
public class JobResource extends BaseResource {

    private static final int MAX_QUEUE_SIZE = Config.getInstance().getPropertyAsInt(HakbotConfigKey.MAX_QUEUE_SIZE);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns all job",
            notes = "Returns a list of all jobs ordered by the time the job was created.",
            response = Job.class,
            responseContainer = "List"
    )
    public Response getAllJobs() {
        try (QueryManager qm = new QueryManager()) {
            final List<Job> jobs = qm.getJobs(QueryManager.OrderDirection.DESC, getPrincipal());
            return Response.ok(jobs).build();
        }
    }

    @GET
    @Path("{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns a specific job",
            notes = "Returns a specific job by it's UUID.",
            response = Job.class
    )
    public Response getJobByUuid(
            @ApiParam(value = "The UUID of the job", required = true)
            @PathParam("uuid") String uuid) {
        final Job job;
        try (QueryManager qm = new QueryManager()) {
            job = qm.getJob(uuid, getPrincipal());
        }
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            return Response.ok(job).build();
        }
    }

    @GET
    @Path("{uuid}/message")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation(
            value = "Returns the messages produced by the job",
            notes = "Returns the messages produced by the job. Depending on the plugin, the amount of messages produced may be large",
            response = String.class
    )
    public Response getJobMessage(
            @ApiParam(value = "The UUID of the job", required = true)
            @PathParam("uuid") String uuid) {
        try (QueryManager qm = new QueryManager()) {
            final String message = qm.getJob(uuid, getPrincipal()).getMessage();
            return Response.ok(message).build();
        }
    }

    @GET
    @Path("{uuid}/payload/provider")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @ApiOperation(
            value = "Returns the provider payload of the job",
            notes = "Returns the provider payload of the job. The payload may be quite large and contain Base64 encoded parameters. The format and syntax of the payload is plugin specific."
    )
    public Response getJobProviderPayload(
            @ApiParam(value = "The UUID of the job", required = true)
            @PathParam("uuid") String uuid,
            @ApiParam(value = "Modifies response behavior", defaultValue = "0", allowableValues = "0,1" )
            @DefaultValue("0") @QueryParam("q") int q) {
        try (QueryManager qm = new QueryManager()) {
            final Job job = qm.getJob(uuid, getPrincipal());
            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            final JobArtifact artifact = qm.getJobArtifact(job, JobArtifact.Type.PROVIDER_PAYLOAD);
            if (artifact == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            final byte[] contents = artifact.getContents();
            if (q == 0) {
                return Response.ok(contents, MediaType.TEXT_PLAIN).build();
            } else if (q == 1) {
                return Response.ok(contents, MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + uuid + "-provider-payload" + "\"")
                        .build();
            }
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    @GET
    @Path("{uuid}/payload/publisher")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @ApiOperation(
            value = "Returns the publisher payload of the job",
            notes = "Returns the publisher payload of the job. The payload may be quite large and contain Base64 encoded parameters. The format and syntax of the payload is plugin specific."
    )
    public Response getJobPublisherPayload(
            @ApiParam(value = "The UUID of the job", required = true)
            @PathParam("uuid") String uuid,
            @ApiParam(value = "Modifies response behavior", defaultValue = "0", allowableValues = "0,1" )
            @DefaultValue("0") @QueryParam("q") int q) {
        try (QueryManager qm = new QueryManager()) {
            final Job job = qm.getJob(uuid, getPrincipal());
            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            final JobArtifact artifact = qm.getJobArtifact(job, JobArtifact.Type.PUBLISHER_PAYLOAD);
            if (artifact == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            final byte[] contents = artifact.getContents();
            if (q == 0) {
                return Response.ok(contents, MediaType.TEXT_PLAIN).build();
            } else if (q == 1) {
                return Response.ok(contents, MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + uuid + "-publisher-payload" + "\"")
                        .build();
            }
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    @GET
    @Path("{uuid}/result")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @ApiOperation(
            value = "Returns the result produced by the job",
            notes = "Returns the result of the job. The result may be quite large and will typically be Base64 encoded when viewed, or automatically Base64 decoded when downloaded."
    )
    public Response getJobResult(
            @ApiParam(value = "The UUID of the job", required = true)
            @PathParam("uuid") String uuid,
            @ApiParam(value = "Modifies response behavior", defaultValue = "0", allowableValues = "0,1,2" )
            @DefaultValue("0") @QueryParam("q") int q) {
        try (QueryManager qm = new QueryManager()) {
            final Job job = qm.getJob(uuid, getPrincipal());
            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            final JobArtifact artifact = qm.getJobArtifact(job, JobArtifact.Type.PROVIDER_RESULT);
            if (artifact == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            final byte[] contents = artifact.getContents();
            if (q == 0) {
                return Response.ok(contents, MediaType.TEXT_PLAIN).build();
            } else if (q == 1) {
                return Response.ok(contents, MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + artifact.getFilename() + "\"")
                        .build();
            }
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Creates a new job",
            notes = "Returns the job after creating it. The UUID can be used to later query on the job. The format of this request will vary largely on the plugins used.",
            response = Job.class)
    public Response addJob(JobRequest jobRequest) {
        if (jobRequest.getName() == null || jobRequest.getProvider() == null ||
                jobRequest.getProvider().getClassname() == null || jobRequest.getProvider().getPayload() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try (QueryManager qm = new QueryManager()) {
            if (qm.getUnprocessedJobCount() > MAX_QUEUE_SIZE) {
                return Response.ok("Queue limit reached. The server is not accepting new jobs. This could be due to a large number of unprocessed jobs or a small limit on the queue. Try again later.")
                        .status(Response.Status.SERVICE_UNAVAILABLE)
                        .build();
            }

            // Retrieve the optional principal for the API key that initiated this request
            final Principal principal = getPrincipal();
            ApiKey apiKey = null;
            if (principal != null) {
                apiKey = (ApiKey) principal;
            }

            final String name = jobRequest.getName();
            final String providerClass = jobRequest.getProvider().getClassname();
            final String providerPayload = JsonUtil.jsonStringFromObject(jobRequest.getProvider().getPayload());
            final String publisherClass = (jobRequest.getPublisher() != null) ? jobRequest.getPublisher()
                    .getClassname() : null;
            final String publisherPayload = (jobRequest.getPublisher() != null) ? JsonUtil.jsonStringFromObject(jobRequest.getPublisher()
                    .getPayload()) : null;

            final Job job = qm.createJob(name, providerClass, providerPayload, publisherClass, publisherPayload, apiKey);
            // At this point, the job has a state of CREATED, which is what we want our response to be.
            EventService.getInstance().publish(new JobUpdateEvent(job.getUuid()).state(State.IN_QUEUE));
            return Response.ok(job).build();
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Purges all jobs from database")
    public Response purgeAll() {
        try (QueryManager qm = new QueryManager()) {
            qm.deleteAllJobs(getPrincipal());
            return Response.ok().build();
        }
    }

    @DELETE
    @Path("/uuid/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Deletes a specific job")
    public Response purgeByUuid(
            @ApiParam(value = "The UUID of the job", required = true)
            @PathParam("uuid") String uuid) {
        try (QueryManager qm = new QueryManager()) {
            qm.deleteJob(uuid, getPrincipal());
            return Response.ok().build();
        }
    }

    @DELETE
    @Path("/state/{state}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Purges all jobs with a specific state from the database",
            notes = "A supported state is required.")
    public Response purge(
            @ApiParam(value = "The job state", required = true)
            @PathParam("state") State state) {
        try (QueryManager qm = new QueryManager()) {
            qm.deleteJobs(state, getPrincipal());
            return Response.ok().build();
        }
    }

}

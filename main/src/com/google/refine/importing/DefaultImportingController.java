package com.google.refine.importing;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.google.refine.RefineServlet;
import com.google.refine.commands.HttpUtilities;
import com.google.refine.importing.ImportingManager.Format;
import com.google.refine.util.JSONUtilities;
import com.google.refine.util.ParsingUtilities;

public class DefaultImportingController implements ImportingController {

    protected RefineServlet servlet;
    
    @Override
    public void init(RefineServlet servlet) {
        this.servlet = servlet;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        // TODO Auto-generated method stub
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        /*
         * The uploaded file is in the POST body as a "file part". If
         * we call request.getParameter() then the POST body will get
         * read and we won't have a chance to parse the body ourselves.
         * This is why we have to parse the URL for parameters ourselves.
         */
        Properties parameters = ParsingUtilities.parseUrlParameters(request);
        String subCommand = parameters.getProperty("subCommand");
        if ("load-raw-data".equals(subCommand)) {
            doLoadRawData(request, response, parameters);
        } else if ("update-file-selection".equals(subCommand)) {
            doUpdateFileSelection(request, response, parameters);
        } else if ("initialize-parser-ui".equals(subCommand)) {
            doInitializeParserUI(request, response, parameters);
        } else if ("update-format-and-options".equals(subCommand)) {
            doUpdateFormatAndOptions(request, response, parameters);
        } else {
            HttpUtilities.respond(response, "error", "No such sub command");
        }
    }

    private void doLoadRawData(HttpServletRequest request, HttpServletResponse response, Properties parameters)
        throws ServletException, IOException {

        long jobID = Long.parseLong(parameters.getProperty("jobID"));
        ImportingJob job = ImportingManager.getJob(jobID);
        if (job == null) {
            HttpUtilities.respond(response, "error", "No such import job");
            return;
        }

        try {
            final JSONObject config = getConfig(job);
            if (!("new".equals(config.getString("state")))) {
                HttpUtilities.respond(response, "error", "Job already started; cannot load more data");
                return;
            }
            
            ImportingUtilities.loadDataAndPrepareJob(
                request, response, parameters, job, config);
        } catch (JSONException e) {
            throw new ServletException(e);
        }
    }
    
    private void doUpdateFileSelection(HttpServletRequest request, HttpServletResponse response, Properties parameters)
        throws ServletException, IOException {
    
        long jobID = Long.parseLong(parameters.getProperty("jobID"));
        ImportingJob job = ImportingManager.getJob(jobID);
        if (job == null) {
            HttpUtilities.respond(response, "error", "No such import job");
            return;
        }
    
        try {
            JSONObject config = getConfig(job);
            if (!("ready".equals(config.getString("state")))) {
                HttpUtilities.respond(response, "error", "Job not ready");
                return;
            }
            
            JSONArray fileSelectionArray = ParsingUtilities.evaluateJsonStringToArray(
                    request.getParameter("fileSelection"));
            
            ImportingUtilities.updateJobWithNewFileSelection(job, fileSelectionArray);
            
            replyWithJobData(request, response, job);
        } catch (JSONException e) {
            throw new ServletException(e);
        }
    }
    
    private void doUpdateFormatAndOptions(HttpServletRequest request, HttpServletResponse response, Properties parameters)
        throws ServletException, IOException {
    
        long jobID = Long.parseLong(parameters.getProperty("jobID"));
        ImportingJob job = ImportingManager.getJob(jobID);
        if (job == null) {
            HttpUtilities.respond(response, "error", "No such import job");
            return;
        }
    
        try {
            JSONObject config = getConfig(job);
            if (!("ready".equals(config.getString("state")))) {
                HttpUtilities.respond(response, "error", "Job not ready");
                return;
            }
            
            String format = request.getParameter("format");
            JSONObject optionObj = ParsingUtilities.evaluateJsonStringToObject(
                    request.getParameter("options"));
            
            List<Exception> exceptions = new LinkedList<Exception>();
            
            ImportingUtilities.previewParse(job, format, optionObj, exceptions);
            
            HttpUtilities.respond(response, "ok", "done");
        } catch (JSONException e) {
            throw new ServletException(e);
        }
    }
    
    private void doInitializeParserUI(HttpServletRequest request, HttpServletResponse response, Properties parameters)
        throws ServletException, IOException {
    
        long jobID = Long.parseLong(parameters.getProperty("jobID"));
        ImportingJob job = ImportingManager.getJob(jobID);
        if (job == null) {
            HttpUtilities.respond(response, "error", "No such import job");
            return;
        }
        
        String format = request.getParameter("format");
        Format formatRecord = ImportingManager.formatToRecord.get(format);
        if (formatRecord != null && formatRecord.parser != null) {
            JSONObject options = formatRecord.parser.createParserUIInitializationData(
                    job, ImportingUtilities.getSelectedFileRecords(job), format);
            JSONObject result = new JSONObject();
            JSONUtilities.safePut(result, "status", "ok");
            JSONUtilities.safePut(result, "options", options);
            
            HttpUtilities.respond(response, result.toString());
        } else {
            HttpUtilities.respond(response, "error", "Unrecognized format or format has no parser");
        }
    }
    
    private JSONObject getConfig(ImportingJob job) {
        if (job.config == null) {
            job.config = new JSONObject();
            JSONUtilities.safePut(job.config, "state", "new");
            JSONUtilities.safePut(job.config, "hasData", false);
        }
        return job.config;
    }
    
    private void replyWithJobData(HttpServletRequest request, HttpServletResponse response, ImportingJob job)
        throws ServletException, IOException {
        
        Writer w = response.getWriter();
        JSONWriter writer = new JSONWriter(w);
        try {
            writer.object();
            writer.key("code"); writer.value("ok");
            writer.key("job"); job.write(writer, new Properties());
            writer.endObject();
        } catch (JSONException e) {
            throw new ServletException(e);
        } finally {
            w.flush();
            w.close();
        }
    }
}

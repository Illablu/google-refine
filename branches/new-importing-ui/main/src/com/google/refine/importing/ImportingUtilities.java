package com.google.refine.importing;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.FileCleaningTracker;
import org.apache.tools.bzip2.CBZip2InputStream;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.refine.importing.ImportingManager.Format;
import com.google.refine.util.JSONUtilities;
import com.ibm.icu.text.NumberFormat;

public class ImportingUtilities {
    static public interface Progress {
        public void setProgress(String message, int percent);
    }
    
    static public void loadDataAndPrepareJob(
        HttpServletRequest request,
        HttpServletResponse response,
        Properties parameters,
        ImportingJob job,
        JSONObject config) throws IOException, ServletException {
        
        JSONObject retrievalRecord = new JSONObject();
        JSONUtilities.safePut(config, "retrievalRecord", retrievalRecord);
        JSONUtilities.safePut(config, "state", "loading-raw-data");
        
        final JSONObject progress = new JSONObject();
        JSONUtilities.safePut(config, "progress", progress);
        try {
            ImportingUtilities.retrieveContentFromPostRequest(
                request,
                parameters,
                job.getRawDataDir(),
                retrievalRecord,
                new Progress() {
                    @Override
                    public void setProgress(String message, int percent) {
                        if (message != null) {
                            JSONUtilities.safePut(progress, "message", message);
                        }
                        JSONUtilities.safePut(progress, "percent", percent);
                    }
                }
            );
        } catch (FileUploadException e) {
            JSONUtilities.safePut(config, "state", "error");
            JSONUtilities.safePut(config, "error", "Error uploading data");
            
            throw new ServletException(e);
        }
        
        JSONArray fileSelectionIndexes = new JSONArray();
        JSONUtilities.safePut(config, "fileSelection", fileSelectionIndexes);
        
        String bestFormat = ImportingUtilities.autoSelectFiles(job, retrievalRecord, fileSelectionIndexes);
        bestFormat = ImportingUtilities.guessBetterFormat(job, bestFormat);
        
        JSONArray rankedFormats = new JSONArray();
        JSONUtilities.safePut(config, "rankedFormats", rankedFormats);
        ImportingUtilities.rankFormats(job, bestFormat, rankedFormats);
        
        JSONUtilities.safePut(config, "state", "ready");
        JSONUtilities.safePut(config, "hasData", true);
        config.remove("progress");
    }
    
    static public void updateJobWithNewFileSelection(ImportingJob job, JSONArray fileSelectionArray) {
        JSONUtilities.safePut(job.config, "fileSelection", fileSelectionArray);
        
        String bestFormat = ImportingUtilities.getCommonFormatForSelectedFiles(job, fileSelectionArray);
        bestFormat = ImportingUtilities.guessBetterFormat(job, bestFormat);
        
        JSONArray rankedFormats = new JSONArray();
        JSONUtilities.safePut(job.config, "rankedFormats", rankedFormats);
        ImportingUtilities.rankFormats(job, bestFormat, rankedFormats);
    }
    
    static public void retrieveContentFromPostRequest(
        HttpServletRequest request,
        Properties parameters,
        File rawDataDir,
        JSONObject retrievalRecord,
        final Progress progress
    ) throws FileUploadException, IOException {
        JSONArray fileRecords = new JSONArray();
        JSONUtilities.safePut(retrievalRecord, "files", fileRecords);
        
        int clipboardCount = 0;
        int uploadCount = 0;
        int downloadCount = 0;
        int archiveCount = 0;
        
        // This tracks the total progress, which involves uploading data from the client
        // as well as downloading data from URLs.
        final SavingUpdate update = new SavingUpdate() {
            @Override
            public void savedMore() {
                progress.setProgress(null, calculateProgressPercent(totalExpectedSize, totalRetrievedSize));
            }
        };
        
        DiskFileItemFactory fileItemFactory = new DiskFileItemFactory();
        fileItemFactory.setFileCleaningTracker(new FileCleaningTracker());
        
        ServletFileUpload upload = new ServletFileUpload(fileItemFactory);
        upload.setProgressListener(new ProgressListener() {
            boolean setContentLength = false;
            long lastBytesRead = 0;
            
            @Override
            public void update(long bytesRead, long contentLength, int itemCount) {
                if (!setContentLength) {
                    // Only try to set the content length if we really know it.
                    if (contentLength >= 0) {
                        update.totalExpectedSize += contentLength;
                        setContentLength = true;
                    }
                }
                if (setContentLength) {
                    update.totalRetrievedSize += (bytesRead - lastBytesRead);
                    lastBytesRead = bytesRead;
                    
                    update.savedMore();
                }
            }
        });

        progress.setProgress("Uploading data ...", -1);
        for (Object obj : upload.parseRequest(request)) {
            FileItem fileItem = (FileItem) obj;
            InputStream stream = fileItem.getInputStream();
            
            String name = fileItem.getFieldName().toLowerCase();
            if (fileItem.isFormField()) {
                if (name.equals("clipboard")) {
                    File file = allocateFile(rawDataDir, "clipboard.txt");
                    
                    JSONObject fileRecord = new JSONObject();
                    JSONUtilities.safePut(fileRecord, "origin", "clipboard");
                    JSONUtilities.safePut(fileRecord, "declaredEncoding", request.getCharacterEncoding());
                    JSONUtilities.safePut(fileRecord, "declaredMimeType", (String) null);
                    JSONUtilities.safePut(fileRecord, "format", "text");
                    JSONUtilities.safePut(fileRecord, "location", getRelativePath(file, rawDataDir));
                    
                    progress.setProgress("Uploading pasted clipboard text",
                        calculateProgressPercent(update.totalExpectedSize, update.totalRetrievedSize));
                    
                    JSONUtilities.safePut(fileRecord, "size", saveStreamToFile(stream, file, null));
                    
                    clipboardCount++;
                    
                    JSONUtilities.append(fileRecords, fileRecord);
                } else if (name.equals("download")) {
                    String urlString = Streams.asString(stream);
                    URL url = new URL(urlString);
                    
                    URLConnection urlConnection = url.openConnection();
                    InputStream stream2 = urlConnection.getInputStream();
                    try {
                        String fileName = url.getFile();
                        File file = allocateFile(rawDataDir, fileName);
                        
                        int contentLength = urlConnection.getContentLength();
                        if (contentLength >= 0) {
                            update.totalExpectedSize += contentLength;
                        }
                        
                        JSONObject fileRecord = new JSONObject();
                        JSONUtilities.safePut(fileRecord, "origin", "download");
                        JSONUtilities.safePut(fileRecord, "declaredEncoding", urlConnection.getContentEncoding());
                        JSONUtilities.safePut(fileRecord, "declaredMimeType", urlConnection.getContentType());
                        JSONUtilities.safePut(fileRecord, "fileName", fileName);
                        JSONUtilities.safePut(fileRecord, "location", getRelativePath(file, rawDataDir));

                        progress.setProgress("Downloading " + urlString,
                            calculateProgressPercent(update.totalExpectedSize, update.totalRetrievedSize));
                        
                        long actualLength = saveStreamToFile(stream, file, update);
                        JSONUtilities.safePut(fileRecord, "size", actualLength);
                        if (contentLength >= 0) {
                            update.totalExpectedSize += (actualLength - contentLength);
                        } else {
                            update.totalExpectedSize += actualLength;
                        }
                        progress.setProgress("Saving " + urlString + " locally",
                            calculateProgressPercent(update.totalExpectedSize, update.totalRetrievedSize));
                        
                        if (postProcessRetrievedFile(file, fileRecord, fileRecords, progress)) {
                            archiveCount++;
                        }

                        downloadCount++;
                    } finally {
                        stream2.close();
                    }
                }

            } else { // is file content
                String fileName = fileItem.getName();
                if (fileName.length() > 0) {
                    long fileSize = fileItem.getSize();
                    
                    File file = allocateFile(rawDataDir, fileName);
                    
                    JSONObject fileRecord = new JSONObject();
                    JSONUtilities.safePut(fileRecord, "origin", "upload");
                    JSONUtilities.safePut(fileRecord, "declaredEncoding", request.getCharacterEncoding());
                    JSONUtilities.safePut(fileRecord, "declaredMimeType", fileItem.getContentType());
                    JSONUtilities.safePut(fileRecord, "fileName", fileName);
                    JSONUtilities.safePut(fileRecord, "location", getRelativePath(file, rawDataDir));

                    progress.setProgress(
                        "Saving file " + fileName + " locally (" + formatBytes(fileSize) + " bytes)",
                        calculateProgressPercent(update.totalExpectedSize, update.totalRetrievedSize));
                    
                    JSONUtilities.safePut(fileRecord, "size", saveStreamToFile(stream, file, null));
                    if (postProcessRetrievedFile(file, fileRecord, fileRecords, progress)) {
                        archiveCount++;
                    }
                    
                    uploadCount++;
                }
            }
        }
        
        JSONUtilities.safePut(retrievalRecord, "uploadCount", uploadCount);
        JSONUtilities.safePut(retrievalRecord, "downloadCount", downloadCount);
        JSONUtilities.safePut(retrievalRecord, "clipboardCount", clipboardCount);
        JSONUtilities.safePut(retrievalRecord, "archiveCount", archiveCount);
    }
    
    static public String getRelativePath(File file, File dir) {
        String location = file.getAbsolutePath().substring(dir.getAbsolutePath().length());
        return (location.startsWith(File.separator)) ? location.substring(1) : location;
    }
    
    static public File allocateFile(File dir, String name) {
        File file = new File(dir, name);

        int dot = name.indexOf('.');
        String prefix = dot < 0 ? name : name.substring(0, dot);
        String suffix = dot < 0 ? "" : name.substring(dot);
        int index = 2;
        while (file.exists()) {
            file = new File(dir, prefix + "-" + index++ + suffix);
        }
        
        file.getParentFile().mkdirs();
        
        return file;
    }
    
    static private abstract class SavingUpdate {
        public long totalExpectedSize = 0;
        public long totalRetrievedSize = 0;
        
        abstract public void savedMore();
    }
    static public long saveStreamToFile(InputStream stream, File file, SavingUpdate update) throws IOException {
        long length = 0;
        FileOutputStream fos = new FileOutputStream(file);
        try {
            byte[] bytes = new byte[4096];
            int c;
            while ((c = stream.read(bytes)) > 0) {
                fos.write(bytes, 0, c);
                length += c;

                if (update != null) {
                    update.totalRetrievedSize += c;
                    update.savedMore();
                }
            }
            return length;
        } finally {
            fos.close();
        }
    }
    
    static public boolean postProcessRetrievedFile(File file, JSONObject fileRecord, JSONArray fileRecords, final Progress progress) {
        
        String mimeType = JSONUtilities.getString(fileRecord, "declaredMimeType", null);
        File rawDataDir = file.getParentFile();
        
        InputStream archiveIS = tryOpenAsArchive(file, mimeType);
        if (archiveIS != null) {
            try {
                if (explodeArchive(rawDataDir, archiveIS, fileRecord, fileRecords, progress)) {
                    file.delete();
                    return true;
                }
            } finally {
                try {
                    archiveIS.close();
                } catch (IOException e) {
                    // TODO: what to do?
                }
            }
        }
        
        InputStream uncompressedIS = tryOpenAsCompressedFile(file, mimeType);
        if (uncompressedIS != null) {
            try {
                File file2 = uncompressFile(rawDataDir, uncompressedIS, fileRecord, progress);
                
                file.delete();
                file = file2;
            } catch (IOException e) {
                // TODO: what to do?
                e.printStackTrace();
            } finally {
                try {
                    archiveIS.close();
                } catch (IOException e) {
                    // TODO: what to do?
                }
            }
        }
        
        postProcessSingleRetrievedFile(file, fileRecord);
        JSONUtilities.append(fileRecords, fileRecord);
        
        return false;
    }
    
    static public void postProcessSingleRetrievedFile(File file, JSONObject fileRecord) {
        JSONUtilities.safePut(fileRecord, "format",
            ImportingManager.getFormat(
                file.getName(),
                JSONUtilities.getString(fileRecord, "declaredMimeType", null)));
    }
    
    static public InputStream tryOpenAsArchive(File file, String mimeType) {
        String fileName = file.getName();
        try {
            if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
                return new TarInputStream(new GZIPInputStream(new FileInputStream(file)));
            } else if (fileName.endsWith(".tar.bz2")) {
                return new TarInputStream(new CBZip2InputStream(new FileInputStream(file)));
            } else if (fileName.endsWith(".tar")) {
                return new TarInputStream(new FileInputStream(file));
            } else if (fileName.endsWith(".zip")) {
                return new ZipInputStream(new FileInputStream(file));
            }
        } catch (IOException e) {
        }
        return null;
    }
    
    static public boolean explodeArchive(
        File rawDataDir,
        InputStream archiveIS,
        JSONObject archiveFileRecord,
        JSONArray fileRecords,
        final Progress progress
    ) {
        if (archiveIS instanceof TarInputStream) {
            TarInputStream tis = (TarInputStream) archiveIS;
            try {
                TarEntry te;
                while ((te = tis.getNextEntry()) != null) {
                    if (!te.isDirectory()) {
                        String fileName2 = te.getName();
                        File file2 = allocateFile(rawDataDir, fileName2);
                        
                        progress.setProgress("Extracting " + fileName2, -1);
                        
                        JSONObject fileRecord2 = new JSONObject();
                        JSONUtilities.safePut(fileRecord2, "origin", JSONUtilities.getString(archiveFileRecord, "origin", null));
                        JSONUtilities.safePut(fileRecord2, "declaredEncoding", (String) null);
                        JSONUtilities.safePut(fileRecord2, "declaredMimeType", (String) null);
                        JSONUtilities.safePut(fileRecord2, "fileName", fileName2);
                        JSONUtilities.safePut(fileRecord2, "archiveFileName", JSONUtilities.getString(archiveFileRecord, "fileName", null));
                        JSONUtilities.safePut(fileRecord2, "location", getRelativePath(file2, rawDataDir));

                        JSONUtilities.safePut(fileRecord2, "size", saveStreamToFile(tis, file2, null));
                        postProcessSingleRetrievedFile(file2, fileRecord2);
                        
                        JSONUtilities.append(fileRecords, fileRecord2);
                    }
                }
            } catch (IOException e) {
                // TODO: what to do?
                e.printStackTrace();
            }
            return true;
        } else if (archiveIS instanceof ZipInputStream) {
            ZipInputStream zis = (ZipInputStream) archiveIS;
            try {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    if (!ze.isDirectory()) {
                        String fileName2 = ze.getName();
                        File file2 = allocateFile(rawDataDir, fileName2);
                        
                        progress.setProgress("Extracting " + fileName2, -1);
                        
                        JSONObject fileRecord2 = new JSONObject();
                        JSONUtilities.safePut(fileRecord2, "origin", JSONUtilities.getString(archiveFileRecord, "origin", null));
                        JSONUtilities.safePut(fileRecord2, "declaredEncoding", (String) null);
                        JSONUtilities.safePut(fileRecord2, "declaredMimeType", (String) null);
                        JSONUtilities.safePut(fileRecord2, "fileName", fileName2);
                        JSONUtilities.safePut(fileRecord2, "archiveFileName", JSONUtilities.getString(archiveFileRecord, "fileName", null));
                        JSONUtilities.safePut(fileRecord2, "location", getRelativePath(file2, rawDataDir));

                        JSONUtilities.safePut(fileRecord2, "size", saveStreamToFile(zis, file2, null));
                        postProcessSingleRetrievedFile(file2, fileRecord2);
                        
                        JSONUtilities.append(fileRecords, fileRecord2);
                    }
                }
            } catch (IOException e) {
                // TODO: what to do?
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }
    
    static public InputStream tryOpenAsCompressedFile(File file, String mimeType) {
        String fileName = file.getName();
        try {
            if (fileName.endsWith(".gz")) {
                return new GZIPInputStream(new FileInputStream(file));
            } else if (fileName.endsWith(".bz2")) {
                return new CBZip2InputStream(new FileInputStream(file));
            }
        } catch (IOException e) {
        }
        return null;
    }
    
    static public File uncompressFile(
        File rawDataDir,
        InputStream uncompressedIS,
        JSONObject fileRecord,
        final Progress progress
    ) throws IOException {
        String fileName = JSONUtilities.getString(fileRecord, "fileName", "unknown");
        File file2 = allocateFile(rawDataDir, fileName);
        
        progress.setProgress("Uncompressing " + fileName, -1);
        
        saveStreamToFile(uncompressedIS, file2, null);
        
        JSONUtilities.safePut(fileRecord, "declaredEncoding", (String) null);
        JSONUtilities.safePut(fileRecord, "declaredMimeType", (String) null);
        JSONUtilities.safePut(fileRecord, "location", getRelativePath(file2, rawDataDir));
        
        return file2;
    }
    
    static private int calculateProgressPercent(long totalExpectedSize, long totalRetrievedSize) {
        return totalExpectedSize == 0 ? -1 : (int) (totalRetrievedSize * 100 / totalExpectedSize);
    }
    
    static private String formatBytes(long bytes) {
        return NumberFormat.getIntegerInstance().format(bytes);
    }
    
    static private String getEncoding(JSONObject fileRecord) {
        String encoding = JSONUtilities.getString(fileRecord, "encoding", null);
        if (encoding == null) {
            encoding = JSONUtilities.getString(fileRecord, "declaredEncoding", null);
        }
        return encoding;
    }

    static public String autoSelectFiles(ImportingJob job, JSONObject retrievalRecord, JSONArray fileSelectionIndexes) {
        final Map<String, Integer> formatToCount = new HashMap<String, Integer>();
        List<String> formats = new ArrayList<String>();
        
        JSONArray fileRecords = JSONUtilities.getArray(retrievalRecord, "files");
        int count = fileRecords.length();
        for (int i = 0; i < count; i++) {
            JSONObject fileRecord = JSONUtilities.getObjectElement(fileRecords, i);
            String format = JSONUtilities.getString(fileRecord, "format", null);
            if (format != null) {
                if (formatToCount.containsKey(format)) {
                    formatToCount.put(format, formatToCount.get(format) + 1);
                } else {
                    formatToCount.put(format, 1);
                    formats.add(format);
                }
            }
        }
        Collections.sort(formats, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return formatToCount.get(o2) - formatToCount.get(o1);
            }
        });
        
        String bestFormat = formats.size() > 0 ? formats.get(0) : null;
        if (JSONUtilities.getInt(retrievalRecord, "archiveCount", 0) == 0) {
            // If there's no archive, then select everything
            for (int i = 0; i < count; i++) {
                JSONUtilities.append(fileSelectionIndexes, i);
            }
        } else {
            // Otherwise, select files matching the best format
            for (int i = 0; i < count; i++) {
                JSONObject fileRecord = JSONUtilities.getObjectElement(fileRecords, i);
                String format = JSONUtilities.getString(fileRecord, "format", null);
                if (format != null && format.equals(bestFormat)) {
                    JSONUtilities.append(fileSelectionIndexes, i);
                }
            }
        }
        return bestFormat;
    }
    
    static public String getCommonFormatForSelectedFiles(ImportingJob job, JSONArray fileSelectionIndexes) {
        JSONObject retrievalRecord = JSONUtilities.getObject(job.config, "retrievalRecord");
        
        final Map<String, Integer> formatToCount = new HashMap<String, Integer>();
        List<String> formats = new ArrayList<String>();
        
        JSONArray fileRecords = JSONUtilities.getArray(retrievalRecord, "files");
        int count = fileSelectionIndexes.length();
        for (int i = 0; i < count; i++) {
            int index = JSONUtilities.getIntElement(fileSelectionIndexes, i, -1);
            if (index >= 0 && index < fileRecords.length()) {
                JSONObject fileRecord = JSONUtilities.getObjectElement(fileRecords, index);
                String format = JSONUtilities.getString(fileRecord, "format", null);
                if (format != null) {
                    if (formatToCount.containsKey(format)) {
                        formatToCount.put(format, formatToCount.get(format) + 1);
                    } else {
                        formatToCount.put(format, 1);
                        formats.add(format);
                    }
                }
            }
        }
        Collections.sort(formats, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return formatToCount.get(o2) - formatToCount.get(o1);
            }
        });
        
        return formats.size() > 0 ? formats.get(0) : null;
    }
    
    static String guessBetterFormat(ImportingJob job, String bestFormat) {
        JSONObject retrievalRecord = JSONUtilities.getObject(job.config, "retrievalRecord");
        return retrievalRecord != null ? guessBetterFormat(job, retrievalRecord, bestFormat) : bestFormat;
    }
    
    static String guessBetterFormat(ImportingJob job, JSONObject retrievalRecord, String bestFormat) {
        JSONArray fileRecords = JSONUtilities.getArray(retrievalRecord, "files");
        return fileRecords != null ? guessBetterFormat(job, fileRecords, bestFormat) : bestFormat;
    }
    
    static String guessBetterFormat(ImportingJob job, JSONArray fileRecords, String bestFormat) {
        if (bestFormat != null && fileRecords != null && fileRecords.length() > 0) {
            JSONObject firstFileRecord = JSONUtilities.getObjectElement(fileRecords, 0);
            String encoding = getEncoding(firstFileRecord);
            String location = JSONUtilities.getString(firstFileRecord, "location", null);
            
            if (location != null) {
                File file = new File(job.getRawDataDir(), location);
                
                while (true) {
                    String betterFormat = null;
                    
                    List<FormatGuesser> guessers = ImportingManager.formatToGuessers.get(bestFormat);
                    if (guessers != null) {
                        for (FormatGuesser guesser : guessers) {
                            betterFormat = guesser.guess(file, encoding, bestFormat);
                            if (betterFormat != null) {
                                break;
                            }
                        }
                    }
                    
                    if (betterFormat != null && !betterFormat.equals(bestFormat)) {
                        bestFormat = betterFormat;
                    } else {
                        break;
                    }
                }
            }
        }
        return bestFormat;
    }
    
    static void rankFormats(ImportingJob job, final String bestFormat, JSONArray rankedFormats) {
        final Map<String, String[]> formatToSegments = new HashMap<String, String[]>();
        
        List<String> formats = new ArrayList<String>(ImportingManager.formatToRecord.keySet().size());
        for (String format : ImportingManager.formatToRecord.keySet()) {
            Format record = ImportingManager.formatToRecord.get(format);
            if (record.uiClass != null && record.parser != null) {
                formats.add(format);
                formatToSegments.put(format, format.split("/"));
            }
        }
        
        if (bestFormat == null) {
            Collections.sort(formats);
        } else {
            Collections.sort(formats, new Comparator<String>() {
                @Override
                public int compare(String format1, String format2) {
                    if (format1.equals(bestFormat)) {
                        return -1;
                    } else if (format2.equals(bestFormat)) {
                        return 1;
                    } else {
                        return compareBySegments(format1, format2);
                    }
                }
                
                int compareBySegments(String format1, String format2) {
                    int c = commonSegments(format2) - commonSegments(format1);
                    return c != 0 ? c : format1.compareTo(format2);
                }
                
                int commonSegments(String format) {
                    String[] bestSegments = formatToSegments.get(bestFormat);
                    String[] segments = formatToSegments.get(format);
                    int i;
                    for (i = 0; i < bestSegments.length && i < segments.length; i++) {
                        if (!bestSegments[i].equals(segments[i])) {
                            break;
                        }
                    }
                    return i;
                }
            });
        }
        
        for (String format : formats) {
            JSONUtilities.append(rankedFormats, format);
        }
    }
    
    static public List<JSONObject> getSelectedFileRecords(ImportingJob job) {
        List<JSONObject> results = new ArrayList<JSONObject>();
        
        JSONObject retrievalRecord = JSONUtilities.getObject(job.config, "retrievalRecord");
        if (retrievalRecord != null) {
            JSONArray fileRecordArray = JSONUtilities.getArray(retrievalRecord, "files");
            if (fileRecordArray != null) {
                JSONArray fileSelectionArray = JSONUtilities.getArray(job.config, "fileSelection");
                if (fileSelectionArray != null) {
                    for (int i = 0; i < fileSelectionArray.length(); i++) {
                        int index = JSONUtilities.getIntElement(fileSelectionArray, i, -1);
                        if (index >= 0 && index < fileRecordArray.length()) {
                            results.add(JSONUtilities.getObjectElement(fileRecordArray, index));
                        }
                    }
                }
            }
        }
        return results;
    }
    
    static public void previewParse(ImportingJob job, String format, JSONObject optionObj, List<Exception> exceptions) {
        Format record = ImportingManager.formatToRecord.get(format);
        if (record == null || record.parser == null) {
            // TODO: what to do?
            return;
        }
        
        job.prepareNewProject();
        
        record.parser.parse(
            job.project,
            job.metadata,
            job,
            getSelectedFileRecords(job),
            format,
            100,
            optionObj,
            exceptions
        );
    }
}

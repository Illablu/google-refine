package com.google.refine.importers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import com.google.refine.importing.FormatGuesser;

public class LineBasedFormatGuesser implements FormatGuesser {

    @Override
    public String guess(File file, String encoding, String seedFormat) {
        try {
            InputStream is = new FileInputStream(file);
            try {
                Reader reader = encoding != null ? new InputStreamReader(is, encoding) : new InputStreamReader(is);
                LineNumberReader lineNumberReader = new LineNumberReader(reader);
                
                int totalBytes = 0;
                int commaCount = 0;
                int tabCount = 0;
                
                String s;
                while (totalBytes < 64 * 1024 && (s = lineNumberReader.readLine()) != null) {
                    commaCount += TextFormatGuesser.countSubstrings(s, ",");
                    tabCount += TextFormatGuesser.countSubstrings(s, "\t");
                    totalBytes += s.length();
                }
                
                if (commaCount > 10 && commaCount > tabCount) {
                    return "text/line-based/*sv/csv";
                } else if (tabCount > 10 && tabCount > commaCount) {
                    return "text/line-based/*sv/tsv";
                }
            } finally {
                is.close();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}

package com.google.refine.importing;

import java.io.File;

public interface FormatGuesser {
    public String guess(File file, String encoding, String seedFormat);
}

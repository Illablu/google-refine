var releases = {
    "homepage" : "http://code.google.com/p/google-refine/wiki/Downloads",
    "releases" : [
        {
            "description": "Google Refine 2.1",
            "version": "2.1",
            "revision": "r2136"
        },
        {
            "description": "Google Refine 2.0",
            "version": "2.0",
            "revision": "r1836"
        },
        {
            "description": "Gridworks 1.1",
            "version": "1.1",
            "revision": "r878"
        },
        {
            "description": "Gridworks 1.0.1",
            "version": "1.0.1",
            "revision": "r732"
        },
        {
            "description": "Gridworks 1.0",
            "version": "1.0",
            "revision": "r667"
        }
    ]
};

// Backward compatibility with 2.0 & 2.1 update checking code
var GoogleRefineReleases;
GoogleRefineReleases.releases = releases;

// This is for back compatibility... remove this after Gridworks usage phases out
var GridworksReleases = releases;
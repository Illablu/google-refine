var releases = {
    "homepage" : "http://code.google.com/p/google-refine/wiki/Downloads",
    "releases" : [
        {
            "description": "Google Refine 2.5",
            "version": "2.5",
            "revision": "r2407"
        },
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

// Backward compatibility with 2.0 & 2.1 update checking code.  It needs both these names
var GoogleRefineReleases = releases;

// This is for back compatibility... remove this after Gridworks usage phases out
var GridworksReleases = releases;

  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

  ga('create', 'UA-35838111-2', 'google-refine.googlecode.com');
  ga('send', 'pageview');


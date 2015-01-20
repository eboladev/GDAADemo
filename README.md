# GDAADemo

GDAADemo is a minimal implementation of SCRU(D) functionality applied to both, 
Google Drive Android API (GDAA), https://developers.google.com/drive/android,
and Google Drive Web APIs (REST) https://developers.google.com/drive/v2/reference  

The SCRUD functionality is not complete, since there is no DELETE in GDAA.
REST has it, but I would not recommend to mix the two interfaces, since
there are timing issues that can't be easily reconciled.

The GooDrive class has 2 sets of methods search, create, read, update that differ by 
ID param type. GDAA methods accept 'DriveId' type, whereas REST methods use 'String' 
type that represents ResourceId (see http://stackoverflow.com/questions/21800257).
The demo is very raw, it dumps its output directly to the screen and is intended
mostly as a tool to step through in debugger.
 
You can select different Google accounts. There is an account manager wrapper in
the UT (utility) class that handles account switching.

GDAADemo has only 2 functions:

1/ UPLOAD (upload icon) will invoke a camera and the resulting picture is uploaded to
Google Drive, creating a simple tree directory structure in the process. 
The createTreeGDAA/createTreeREST methods allow to test different methods
(search, create folder, ...) in the process.

2/ DOWNLOAD (download icon) scans the tree created by GDAADemo 
(testTreeGDAA/testTreeREST methods). If the object is a file (jpg), it's metadata is 
updated (description field) and the content is downloaded (and dumped) - only 
the number of bytes is reported.

It should be noted, that the code uses the simplest implementation, the 'AWAIT' 
flavor of the GDAA calls. It must be implemented in non-UI thread, but
the code is simpler, best suited for testing. This also allows me to keep 
GDAA methods compatible with the REST counterparts.

Hope it helps.

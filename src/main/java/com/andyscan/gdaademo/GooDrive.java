package com.andyscan.gdaademo;

/**
 * Copyright 2014 Sean Janson. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **/

import android.os.AsyncTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Contents;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveApi.MetadataBufferResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveFolder.DriveFileResult;
import com.google.android.gms.drive.DriveFolder.DriveFolderResult;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource.MetadataResult;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.MetadataChangeSet.Builder;
import com.google.android.gms.drive.events.ChangeEvent;
import com.google.android.gms.drive.events.DriveEvent;
import com.google.android.gms.drive.query.Filter;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.json.gson.GsonFactory;

import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.Drive.Files.List;
import com.google.api.services.drive.model.ParentReference;

final class GooDrive { private GooDrive() {}

  private static GoogleApiClient mGAC;
  private static com.google.api.services.drive.Drive mGOOSvc;

  /************************************************************************************************
   * initialize Google Drive Api
   * @param ctx   activity context
   * @param email  GOO account
   */
  static void init(MainActivity ctx, String email){                          UT.lg( "init GDAA ");
    if (ctx != null && email != null) {
      //connect(false);
      mGAC = new GoogleApiClient.Builder(ctx).addApi(Drive.API)
       .addScope(Drive.SCOPE_FILE).setAccountName(email)
       .addConnectionCallbacks(ctx).addOnConnectionFailedListener(ctx).build();

      mGOOSvc = new com.google.api.services.drive.Drive.Builder(
       AndroidHttp.newCompatibleTransport(), new GsonFactory(), GoogleAccountCredential
       .usingOAuth2(ctx, Arrays.asList(DriveScopes.DRIVE_FILE))
       .setSelectedAccountName(email)
      ).build();

    }

  }
  /************************************************************************************************
   * connect / disconnect
   */
  static void connect(boolean bOn) {
    if (mGAC != null) {
      if (bOn) {
        if (!mGAC.isConnected()) {                            UT.lg( "connect ");
          mGAC.connect();
        }
      } else {
        if (mGAC.isConnected()) {                             UT.lg( "disconnect ");
          mGAC.disconnect();
        }
      }
    }
  }
  private static boolean isConnected() {return mGAC != null && mGAC.isConnected();}

  //////////////////////////////////////////////////////////////////////////////////////////////
  // (S)CRU(D) implementation of Google Drive Android API (GDAA) ////////////////////////////
  /************************************************************************************************
   * find file/folder in GOODrive
   * @param titl    file/folder name (optional)
   * @param mime    file/folder mime type (optional)
   * @return        file/folder ID / null on fail
   */
  static ArrayList<UT.GF> search(DriveId prId, String titl, String mime) {
    ArrayList<UT.GF> gfs = new ArrayList<>();

    if (isConnected()) {
      // add query conditions, build query
      ArrayList<Filter> fltrs = new ArrayList<>();
      if (prId != null) fltrs.add(Filters.in(SearchableField.PARENTS, prId));
      if (titl != null) fltrs.add(Filters.eq(SearchableField.TITLE, titl));
      if (mime != null) fltrs.add(Filters.eq(SearchableField.MIME_TYPE, mime));
      Query qry = new Query.Builder().addFilter(Filters.and(fltrs)).build();

      // fire the query
      MetadataBufferResult rslt = Drive.DriveApi.query(mGAC, qry).await();
      if (rslt.getStatus().isSuccess()) {
        MetadataBuffer mdb = null;
        try {
          mdb = rslt.getMetadataBuffer();
          for (Metadata md : mdb) {
            if (md == null || !md.isDataValid() || md.isTrashed()) continue;
            // NULL title value indicates 'deleted' state
            gfs.add(new UT.GF(md.getTitle(), md.getDriveId().encodeToString()));
          }
        } finally { if (mdb != null) mdb.close(); }
      }
    }
    return gfs;
  }

  /************************************************************************************************
   * create file/folder in GOODrive
   * @param prId  parent's ID, null for root
   * @param titl  file name
   * @param mime  file mime type
   * @param buf   file contents  (optional, if null, create folder)
   * @return      file id  / null on fail
   */
  static DriveId create(DriveId prId, String titl, String mime, byte[] buf) {
    DriveId dId = null;
    if (titl != null && isConnected()) {
      DriveFolder pFld =
       (prId == null) ? Drive.DriveApi.getRootFolder(mGAC) : Drive.DriveApi.getFolder(mGAC, prId);
      if (pFld == null) return null; //----------------->>>

      if (buf != null) {  // create file
        if (mime != null) {   // file must have mime
          DriveContentsResult ctRs = Drive.DriveApi.newDriveContents(mGAC).await();
          if ((ctRs != null) && (ctRs.getStatus().isSuccess())) {
            MetadataChangeSet meta =
             new MetadataChangeSet.Builder().setTitle(titl).setMimeType(mime).build();
            DriveFileResult dvRs = pFld.createFile(mGAC, meta, ctRs.getDriveContents()).await();
            DriveFile dFil = dvRs != null && dvRs.getStatus().isSuccess() ?
             dvRs.getDriveFile() : null;
            if (dFil != null) {
              ctRs = dFil.open(mGAC, DriveFile.MODE_WRITE_ONLY, null).await();
              if ((ctRs != null ) && (ctRs.getStatus().isSuccess())) try {
                DriveContents dc = ctRs.getDriveContents();
                dc.getOutputStream().write(buf);
                Status stts = dc.commit(mGAC, meta).await(); //.commitAndCloseContents(mGAC, ctRs.getDriveContents()).await();
                if ((stts != null) && stts.isSuccess()) {
                  MetadataResult mdRs = dFil.getMetadata(mGAC).await();
                  if ((mdRs != null) && mdRs.getStatus().isSuccess()) {
                    dId = mdRs.getMetadata().getDriveId();
                  }
                }
              } catch (Exception e) {UT.le(e);}
            }
          }
        }
      } else {
        MetadataChangeSet meta =
         new MetadataChangeSet.Builder().setTitle(titl).setMimeType(UT.MIME_FLDR).build();
        DriveFolderResult rs0 = pFld.createFolder(mGAC, meta).await();
        DriveFolder dFld =
         (rs0 != null) && rs0.getStatus().isSuccess() ? rs0.getDriveFolder() : null;
        if (dFld != null) {
          MetadataResult rs1 = dFld.getMetadata(mGAC).await();
          if ((rs1 != null) && rs1.getStatus().isSuccess()) {
            dId = rs1.getMetadata().getDriveId();
          }
        }
      }
      }
    return dId;
  }
  /************************************************************************************************
   * get file contents
   * @param dId       file driveId
   * @return          file's content  / null on fail
   */
  static byte[] read(DriveId dId) {
    byte[] buf = null;
    if (isConnected()) {
      DriveFile df = Drive.DriveApi.getFile(mGAC, dId);
      DriveContentsResult rslt = df.open(mGAC, DriveFile.MODE_READ_ONLY, null).await();
      //ContentsResult rslt = df.openContents(mGAC, DriveFile.MODE_READ_ONLY, null).await();
      if ((rslt != null) && rslt.getStatus().isSuccess()) {
        DriveContents cont = rslt.getDriveContents();
        InputStream is = cont.getInputStream();
        buf = UT.is2Bytes(is);
        cont.discard(mGAC);
      }
    }
    return buf;
  }
  /************************************************************************************************
   * update file in GOODrive
   * @param dId   file  id
   * @param titl  new file name (optional)
   * @param mime  new file mime type (optional, null or MIME_FLDR indicates folder)
   * @param buf   new file contents (optional)
   * @return       success status
   */
  static boolean update(DriveId dId, String titl, String mime, String desc, byte[] buf){
    Boolean bOK = false;
    if (dId != null && isConnected()) {
      Builder mdBd = new MetadataChangeSet.Builder();
      if (titl != null) mdBd.setTitle(titl);

      if (mime == null || UT.MIME_FLDR.equals(mime)) {
        DriveFolder dFld = Drive.DriveApi.getFolder(mGAC, dId);
        MetadataResult rs0 = dFld.updateMetadata(mGAC, mdBd.build()).await();
        bOK = (rs0 != null) && rs0.getStatus().isSuccess();
      } else {
        DriveFile dFil = Drive.DriveApi.getFile(mGAC, dId);
        mdBd.setMimeType(mime);
        if (desc != null) mdBd.setDescription(desc);
        MetadataChangeSet meta = mdBd.build();
        MetadataResult rs0 = dFil.updateMetadata(mGAC, meta).await();
        bOK = (rs0 != null) && rs0.getStatus().isSuccess();
        if (buf != null) {
          DriveContentsResult rs1 = dFil.open(mGAC, DriveFile.MODE_WRITE_ONLY, null).await();
          //ContentsResult rs1 = dFil.openContents(mGAC, DriveFile.MODE_WRITE_ONLY, null).await();
          if (rs1.getStatus().isSuccess()) try {
            DriveContents dc = rs1.getDriveContents();
            dc.getOutputStream().write(buf);
            //Status stts = dFil.commitAndCloseContents(mGAC, rs1.getContents()).await();
            Status stts = dc.commit(mGAC, meta).await();
            return bOK && (stts != null && stts.isSuccess());
          } catch (Exception e) { UT.le(e);}
        }
      }
    }
    return bOK;
  }

  /************************************************************************************************
   * builds a file tree MYROOT/yyyy-mm/yymmdd-hhmmss.jpg
   * @param root  MYROOT
   * @param titl  new file name
   * @param buf   new file contents
   * @return      file Id
   */
  static DriveId createTreeGDAA(String root, String titl, byte[] buf) {
    if (root == null || titl == null) return null;
    DriveId dId = findOrCreateFolder((DriveId)null, root);
    if (dId != null) {
      dId = findOrCreateFolder(dId, UT.titl2Month(titl));
      if (dId != null) {
        return create(dId, titl + UT.JPEG_EXT, UT.MIME_JPEG, buf);
      }
    }
    return null;
  }
  /************************************************************************************************
   * runs a test scanning GooDrive, downloading and updating jpegs
   * @param root  MYROOT
   */
  static ArrayList<UT.GF> testTreeGDAA(String root) {
    ArrayList<UT.GF> gfs0 = search((DriveId)null, root, null);
    if (gfs0 != null) for (UT.GF gf0 : gfs0) {
      ArrayList<UT.GF> gfs1 = search(DriveId.decodeFromString(gf0.id), null, null);
      if (gfs1 != null) for (UT.GF gf1 : gfs1) {
        gfs0.add(gf1);
        ArrayList<UT.GF> gfs2 = search(DriveId.decodeFromString(gf1.id), null, null);
        if (gfs2 != null) for (UT.GF gf2 : gfs2) {
          UT.TM.reset();
          byte[] buf = read(DriveId.decodeFromString(gf2.id));
          if (buf != null) {
            gf2.titl += (", " + (buf.length/1024) + " kB ");
          } else {
            gf2.titl += (" failed to download ");
          }
          update(DriveId.decodeFromString(gf2.id), null, null, "seen " + UT.time2Titl(null), null);
          gf2.titl += ("in " + UT.TM.reset() + "s");
          gfs0.add(gf2);
        }
      }
    }
    return gfs0;
  }

  private static DriveId findOrCreateFolder(DriveId prnt, String titl){
    ArrayList<UT.GF> gfs = search(prnt, titl, UT.MIME_FLDR);
    if (gfs.size() > 0) {
      return DriveId.decodeFromString(gfs.get(0).id);
    }
    return create(prnt, titl, null, null);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////
  // (S)CRU(D) implementation of Google APIs Java Client (REST) /////////////////////////
  /************************************************************************************************
   * find file/folder in GOODrive
   * @param prId   parent folder resource id (optional)
   * @param titl   file/folder name (optional)
   * @param mime    file/folder mime type (optional)
   * @return        arraylist of found objects
   */
  static ArrayList<UT.GF> search(String prId, String titl, String mime) {
    ArrayList<UT.GF> gfs = new ArrayList<>();
    if (isConnected()) {
      String qryClause = "'me' in owners and ";
      if (prId != null) qryClause += "'" + prId + "' in parents and ";
      if (titl != null) qryClause += "title = '" + titl + "' and ";
      if (mime != null) qryClause += "mimeType = '" + mime + "' and ";
      qryClause = qryClause.substring(0, qryClause.length() - " and ".length());
      List qry = null;
      try {
        qry = mGOOSvc.files().list().setQ(qryClause)
         .setFields("items(id, labels/trashed, title), nextPageToken");
        gfs = search(gfs, qry);
      } catch (GoogleAuthIOException gaiEx) {     // WTF ???
        try {
          gfs = search(gfs, qry);
        } catch (Exception g) { UT.le(g);       }
      } catch (Exception e) { UT.le(e); }
    }
    return gfs;
  }
  /************************************************************************************************
   * create file/folder in GOODrive
   * @param prId  parent's ID, null for root
   * @param titl  file name
   * @param mime  file mime type
   * @param buf   file content (optional, if null, create folder)
   * @return      file id  / null on fail
   */
  static String create(String prId, String titl, String mime, byte[] buf) {
    String rsid = null;
    if (titl != null && isConnected()) {

      File meta = new File();
      meta.setParents(Arrays.asList(new ParentReference().setId(prId==null ? "root" : prId)));
      meta.setTitle(titl);

      File gFl = null;
      if (buf != null) {  // create file
        if (mime != null) {   // file must have mime
          meta.setMimeType(mime);
          java.io.File jvFl;
          try {
            jvFl =  UT.bytes2File(buf,
                          java.io.File.createTempFile(UT.TMP_FILENM, null, UT.acx.getCacheDir()));
            gFl = mGOOSvc.files().insert(meta, new FileContent(mime, jvFl)).execute();
          } catch (Exception e) { UT.le(e); }
          if (gFl != null && gFl.getId() != null) {
            rsid = gFl.getId();
          }
        }
      } else {    // create folder
        meta.setMimeType(UT.MIME_FLDR);
        try { gFl = mGOOSvc.files().insert(meta).execute();
        } catch (Exception e) { UT.le(e); }
        if (gFl != null && gFl.getId() != null) {
          rsid = gFl.getId();
        }
      }
    }
    return rsid;
  }
  /************************************************************************************************
   * get file contents
   * @param rsId      file id
   * @return          file's content  / null on fail
   */
  static byte[] read(String rsId) {
    byte[] buf = null;
    if (isConnected() && rsId != null) try {
      File gFl = mGOOSvc.files().get(rsId).setFields("downloadUrl").execute();
      if (gFl != null){
        InputStream is = mGOOSvc.getRequestFactory()
         .buildGetRequest(new GenericUrl(gFl.getDownloadUrl())).execute().getContent();
        buf = UT.is2Bytes(is);
      }
    } catch (Exception e) { UT.le(e); }
    return buf;
  }
  /************************************************************************************************
   * update file in GOODrive
   * @param rsid  file  id
   * @param titl  new file name (optional)
   * @param mime  new file mime type (optional, null or MIME_FLDR indicates folder)
   * @param buf   new file content (optional)
   * @return       success status
   */
  static boolean update(String rsid, String titl, String mime, String desc, byte[] buf){
    Boolean bOK = false;
    java.io.File jvFl = null;
    if (mGOOSvc != null && rsid != null) try {
      File body = new File();
      if (titl != null) body.setTitle(titl);
      if (mime != null) body.setMimeType(mime);
      if (desc != null) body.setDescription(desc);
      jvFl = UT.bytes2File(buf,
                     java.io.File.createTempFile(UT.TMP_FILENM, null, UT.acx.getCacheDir()));
      FileContent cont = jvFl != null ? new FileContent(mime, jvFl) : null;
      File gFl = (cont == null) ? mGOOSvc.files().patch(rsid, body).execute() :
       mGOOSvc.files().update(rsid, body, cont).setOcr(false).execute();
      bOK = gFl != null && gFl.getId() != null;
    } catch (Exception e) { UT.le(e);  }
    finally { if (jvFl != null) jvFl.delete(); }
    return bOK;
  }

  /************************************************************************************************
   * builds a file tree MYROOT/yyyy-mm/yymmdd-hhmmss.jpg
   * @param root  MYROOT
   * @param titl  new file name
   * @param buf   new file contents
   */
  static void createTreeREST(String root, String titl, byte[] buf) {
    if (root != null && titl != null) {
      String rsid = findOrCreateFolder("root", root);
      if (rsid != null) {
        rsid = findOrCreateFolder(rsid, UT.titl2Month(titl));
        if (rsid != null) {
          create(rsid, titl + UT.JPEG_EXT, UT.MIME_JPEG, buf);
        }
      }
    }
  }
  /************************************************************************************************
   * runs a test scanning GooDrive, downloading and updating jpegs
   * @param root  MYROOT
   */
  static ArrayList<UT.GF> testTreeREST(String root) {
    ArrayList<UT.GF> gfs0 = search((String)null, root, null);
    if (gfs0 != null) {
      for (UT.GF gf0 : gfs0) {
        ArrayList<UT.GF> gfs1 = search(gf0.id, null, null);
        if (gfs1 != null) {
          gfs0.addAll(gfs1);
          for (UT.GF gf1 : gfs1) {
            ArrayList<UT.GF> gfs2 = search(gf1.id, null, null);
            if (gfs2 != null) {
              for (UT.GF gf2 : gfs2) {
                UT.TM.reset();
                byte[] buf = read(gf2.id);
                if (buf != null) {
                  gf2.titl += (", " + (buf.length/1024) + " kB ");
                } else {
                  gf2.titl += (" failed to download ");
                }
                update(gf2.id, null, null, "seen " + UT.time2Titl(null), null);
                gf2.titl += ("in " + UT.TM.reset() + "s");
                gfs0.add(gf2);
              }
            }
          }
        }
      }
    }
    return gfs0;
  }

  private static ArrayList<UT.GF> search(ArrayList<UT.GF> gfs, List qry) throws IOException {
    String npTok = null;
    if (qry != null) do {
      FileList gLst = qry.execute();
      if (gLst != null) {
        for (File gFl : gLst.getItems()) {
          if (gFl.getLabels().getTrashed()) continue;
          gfs.add(new UT.GF(gFl.getTitle(), gFl.getId()));
        }                                            //else UT.lg("failed " + gFl.getTitle());
        npTok = gLst.getNextPageToken();
        qry.setPageToken(npTok);
      }
    } while (npTok != null && npTok.length() > 0);           //UT.lg("found " + vlss.size());
    return gfs;
  }
  private static String  findOrCreateFolder(String prnt, String titl){
    ArrayList<UT.GF> gfs = search(prnt, titl, UT.MIME_FLDR);
    if (gfs.size() > 0) {
      return gfs.get(0).id;
    }
    return create(prnt, titl, null, null);
  }
}

/***
 //DriveId dId = md.getDriveId();
 //DriveId.decodeFromString(dId.encodeToString())
 //dId.getResourceId()
 //md.getDescription(), md.getTitle(),
 //md.getAlternateLink(), md.getEmbedLink(), md.getWebContentLink(), md.getWebViewLink()
 ***/

package kr.wooritalk.app;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;

import java.io.File;

/** Registers completed image/video downloads with MediaStore so Gallery apps can see them. */
public class DownloadCompleteReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id=intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1L);
        if(id<0)return;
        SharedPreferences p=context.getSharedPreferences("wooritalk_downloads",Context.MODE_PRIVATE);
        String mime=p.getString("mime_"+id,"");
        String name=p.getString("name_"+id,"");
        if(!(mime.startsWith("image/")||mime.startsWith("video/"))){cleanup(p,id);return;}
        DownloadManager dm=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        Cursor c=null;
        try{
            c=dm.query(new DownloadManager.Query().setFilterById(id));
            if(c==null||!c.moveToFirst()){cleanup(p,id);return;}
            int statusIdx=c.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if(statusIdx>=0 && c.getInt(statusIdx)!=DownloadManager.STATUS_SUCCESSFUL){cleanup(p,id);return;}

            // DownloadManager may expose a content:// URI on modern Android.  Because this app
            // explicitly saves to the public Download directory, scan the known filesystem path
            // first; this works across Android 9+ and causes Gallery/Photos apps to index it.
            File downloadDir=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File saved=new File(downloadDir,name);
            if(saved.isFile()){
                MediaScannerConnection.scanFile(context,new String[]{saved.getAbsolutePath()},new String[]{mime},null);
            }else{
                int idx=c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                String local=idx>=0?c.getString(idx):null;
                if(local!=null){
                    Uri u=Uri.parse(local);
                    if("file".equalsIgnoreCase(u.getScheme())&&u.getPath()!=null){
                        MediaScannerConnection.scanFile(context,new String[]{u.getPath()},new String[]{mime},null);
                    }
                }
            }
        }catch(Exception ignored){}finally{if(c!=null)c.close();cleanup(p,id);}
    }
    private static void cleanup(SharedPreferences p,long id){p.edit().remove("mime_"+id).remove("name_"+id).apply();}
}

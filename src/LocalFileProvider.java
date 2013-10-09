/*------------------------------------------------------------------------
* (The MIT License)
* 
* Copyright (c) 2008-2011 Rhomobile, Inc.
* 
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
* 
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
* 
* http://rhomobile.com
*------------------------------------------------------------------------*/



import java.io.File;
import java.io.FileNotFoundException;

import com.rhomobile.rhodes.file.RhoFileApi;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;


public class LocalFileProvider extends ContentProvider
{
    private static final String TAG = LocalFileProvider.class.getSimpleName();
    public static final String PATH_PREFIX = "/data/data/";
    public static final String PROTOCOL_PREFIX = "content://";

    public static boolean isCorrectAuthority(String authority)
    {
//        Logger.D(TAG, "Comparing authorities: "
//                    + RhodesService.getInstance().getClass().getPackage().getName()
//                    + " vs. " + authority);
//        return authority.equals(RhodesService.getInstance().getClass().getPackage().getName());
        return true;
    }
    
    public static Uri uriFromLocalFile(File file)
    {
        String path = file.getAbsolutePath();
        String url = PROTOCOL_PREFIX +
            path.substring(PATH_PREFIX.length(), path.length());
        return Uri.parse(url);
    }
    
    public static void revokeUriPermissions(Context ctx)
    {
        String rootUri = PROTOCOL_PREFIX + '/' + ctx.getPackageName();

        Logger.I(TAG, "Revoke URI permissions: " + rootUri);

        ctx.revokeUriPermission(Uri.parse(rootUri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    
    public static File fileFromUri(Uri uri) throws IllegalArgumentException
    {
        String authority = uri.getAuthority();
        if(isCorrectAuthority(authority))
            return new File(PATH_PREFIX + authority + uri.getPath());
        else throw new IllegalArgumentException("Unknown URI authority: " + authority);
    }
    
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
        throws FileNotFoundException, SecurityException
    {
        Logger.T(TAG, "Opening content: " + uri);
        
        if(mode.compareTo("r") != 0)
        {
            throw new SecurityException("Unacceptable openFile mode: " + mode);
        }
        
        try {
            File path = fileFromUri(uri);
            
            Logger.D(TAG, "Opening content file: " + path.getPath());
            
            ParcelFileDescriptor fd = RhoFileApi.openParcelFd(path.getPath());
            if(fd == null)
                throw new IllegalArgumentException();
            
            return fd;
        } catch(IllegalArgumentException error)
        {
            FileNotFoundException fileError = new FileNotFoundException("Cannot assign file for URI: " + uri.toString());
            fileError.initCause(error);
            throw fileError;
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public int delete(Uri uri, String s, String[] as) {
        throw new UnsupportedOperationException(
                "Delete operation is not supported by this provider");
    }

    @Override
    public String getType(Uri uri) {

        Logger.D(TAG, "Resolving content type - " + uri);

        String type = "";
        String ext = MimeTypeMap.getFileExtensionFromUrl(uri.getPath());
        if (ext == null || ext.length() == 0)
            throw new IllegalArgumentException("Unknown URI " + uri);

        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return type;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentvalues) {
        throw new UnsupportedOperationException(
                "Insert operation is not supported by this provider");
    }

    @Override
    public Cursor query(Uri uri, String[] as, String s, String[] as1, String s1) {
        throw new UnsupportedOperationException(
                "Query operation is not supported by this provider");
    }

    @Override
    public int update(Uri uri, ContentValues contentvalues, String s, String[] as) {
        throw new UnsupportedOperationException(
                "Update operation is not supported by this provider");
    }

}

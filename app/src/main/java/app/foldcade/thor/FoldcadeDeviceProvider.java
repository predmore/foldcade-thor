/* SPDX-License-Identifier: Apache-2.0 */
package app.foldcade.thor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.CancellationSignal;
import android.util.Log;
import java.io.FileNotFoundException;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

public final class FoldcadeDeviceProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; } // No root, storage or capability work here.

    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        String caller = null;
        boolean allowed = false;
        try {
            caller = getCallingPackage();
            if (caller != null && CallerPolicy.PINS.containsKey(caller)) {
                PackageInfo info = getContext().getPackageManager().getPackageInfo(caller, PackageManager.GET_SIGNING_CERTIFICATES);
                Set<String> signers = new HashSet<>();
                if (info.signingInfo != null) for (Signature signer : info.signingInfo.getApkContentsSigners()) {
                    byte[] digest = MessageDigest.getInstance("SHA-256").digest(signer.toByteArray());
                    StringBuilder hex = new StringBuilder();
                    for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
                    signers.add(hex.toString());
                }
                allowed = info.applicationInfo != null && CallerPolicy.allows(caller, uid, info.applicationInfo.uid, signers);
            }
        } catch (Exception ignored) { /* Fail closed, including a binder/package mismatch. */ }
        if (!allowed) {
            Log.w("FoldcadeThor", "Rejected device caller package=" + caller + " uid=" + uid);
            return DeviceController.error("not_allowed", "Caller is not an authenticated Foldcade build");
        }
        // The caller decision above is complete before even constructing the root owner.
        DeviceController controller = DeviceController.get(getContext());
        try { return controller.submit(() -> controller.handle(method, arg, extras)).get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return DeviceController.error("internal", "Call interrupted"); }
        catch (Exception e) { Log.e("FoldcadeThor", "Provider call failed", e); return DeviceController.error("internal", "Device call failed"); }
    }
    private static SecurityException refused() { return new SecurityException("Only authenticated call() is supported"); }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { throw refused(); }
    @Override public String getType(Uri uri) { throw refused(); }
    @Override public String[] getStreamTypes(Uri uri, String filter) { throw refused(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw refused(); }
    @Override public int bulkInsert(Uri uri, ContentValues[] values) { throw refused(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw refused(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw refused(); }
    @Override public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) { throw refused(); }
    @Override public Uri canonicalize(Uri uri) { throw refused(); }
    @Override public Uri uncanonicalize(Uri uri) { throw refused(); }
    @Override public boolean refresh(Uri uri, Bundle args, CancellationSignal cancellation) { throw refused(); }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException { throw refused(); }
}

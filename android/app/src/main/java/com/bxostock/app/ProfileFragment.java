package com.bxostock.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.ChangePasswordRequest;
import com.bxostock.app.model.PhotoRequest;
import com.bxostock.app.model.PhotoResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Session;
import com.bxostock.app.util.TokenStore;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {
    private TextInputEditText etCurrent, etNew, etConfirm;
    private TextView tvMessage, tvPhotoMsg, tvInitial;
    private ImageView ivPhoto;
    private View btn, btnRemove;

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && isAdded()) processPhoto(uri);
            });

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok != null && ok && isAdded()) processPhoto(cameraUri());
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        String name = TokenStore.name(requireContext());
        ((TextView) v.findViewById(R.id.tvName)).setText(name);
        ((TextView) v.findViewById(R.id.tvEmail)).setText(TokenStore.email(requireContext()));
        ((TextView) v.findViewById(R.id.tvRole)).setText(TokenStore.role(requireContext()));
        etCurrent = v.findViewById(R.id.etCurrent);
        etNew = v.findViewById(R.id.etNew);
        etConfirm = v.findViewById(R.id.etConfirm);
        tvMessage = v.findViewById(R.id.tvMessage);
        btn = v.findViewById(R.id.btnChange);
        btn.setOnClickListener(x -> submit());

        ivPhoto = v.findViewById(R.id.ivPhoto);
        tvInitial = v.findViewById(R.id.tvInitial);
        tvPhotoMsg = v.findViewById(R.id.tvPhotoMsg);
        btnRemove = v.findViewById(R.id.btnRemovePhoto);
        ivPhoto.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        ivPhoto.setClipToOutline(true);
        tvInitial.setText(name == null || name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase());

        v.findViewById(R.id.btnGallery).setOnClickListener(x -> pickImage.launch("image/*"));
        v.findViewById(R.id.btnCamera).setOnClickListener(x -> {
            new File(requireContext().getCacheDir(), "photos/camera.jpg").delete();
            takePicture.launch(cameraUri());
        });
        btnRemove.setOnClickListener(x -> removePhoto());
        loadPhoto();
    }

    // ---------------- photo ----------------

    private Uri cameraUri() {
        File dir = new File(requireContext().getCacheDir(), "photos");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, "camera.jpg");
        return FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".photos", f);
    }

    private void photoMsg(String text, boolean error) {
        if (tvPhotoMsg == null) return;
        tvPhotoMsg.setTextColor(error ? 0xFFFF6B6B : 0xFF3DDC84);
        tvPhotoMsg.setText(text);
        tvPhotoMsg.setVisibility(View.VISIBLE);
    }

    private void loadPhoto() {
        ApiClient.get(requireContext()).getPhoto().enqueue(new Callback<PhotoResponse>() {
            @Override
            public void onResponse(Call<PhotoResponse> c, Response<PhotoResponse> r) {
                if (!isAdded()) return;
                if (r.isSuccessful() && r.body() != null && r.body().photo != null) show(r.body().photo);
            }

            @Override
            public void onFailure(Call<PhotoResponse> c, Throwable t) { }
        });
    }

    private void show(String dataUrl) {
        try {
            byte[] bytes = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null) {
                ivPhoto.setImageBitmap(bmp);
                tvInitial.setVisibility(View.GONE);
                btnRemove.setVisibility(View.VISIBLE);
                return;
            }
        } catch (Exception ignored) {
        }
        clearPhoto();
    }

    private void clearPhoto() {
        ivPhoto.setImageDrawable(null);
        tvInitial.setVisibility(View.VISIBLE);
        btnRemove.setVisibility(View.GONE);
    }

    private void processPhoto(final Uri uri) {
        photoMsg("Saving photo...", false);
        final ContentResolver cr = requireContext().getApplicationContext().getContentResolver();
        new Thread(() -> {
            String dataUrl = null;
            String problem = null;
            try {
                dataUrl = toDataUrl(decode(cr, uri));
            } catch (Exception | OutOfMemoryError ex) {
                problem = "This photo could not be read. Try another one.";
            }
            final String d = dataUrl;
            final String p = problem;
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (d == null) photoMsg(p, true);
                else upload(d);
            });
        }).start();
    }

    private static Bitmap decode(ContentResolver cr, Uri uri) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 600 && o.outHeight / (sample * 2) >= 600) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = cr.openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, o2);
        }
        if (bmp == null) throw new IOException("unreadable");
        int rotation = 0;
        try (InputStream in = cr.openInputStream(uri)) {
            ExifInterface ex = new ExifInterface(in);
            int ori = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
        } catch (Exception ignored) {
        }
        if (rotation != 0) {
            Matrix mx = new Matrix();
            mx.postRotate(rotation);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mx, true);
        }
        return bmp;
    }

    /** Centre-crops to a square, shrinks to 256 px and returns a JPEG data URL. */
    private static String toDataUrl(Bitmap src) {
        int side = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        Bitmap small = Bitmap.createScaledBitmap(sq, 256, 256, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 82, out);
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private void upload(final String dataUrl) {
        PhotoRequest req = new PhotoRequest();
        req.photo = dataUrl;
        ApiClient.get(requireContext()).savePhoto(req).enqueue(new Callback<PhotoResponse>() {
            @Override
            public void onResponse(Call<PhotoResponse> c, Response<PhotoResponse> r) {
                if (!isAdded()) return;
                if (r.isSuccessful()) {
                    show(dataUrl);
                    photoMsg("Profile photo updated.", false);
                } else if (r.code() == 401) {
                    Session.expire(requireActivity(), true);
                } else {
                    photoMsg(ApiError.message(r), true);
                }
            }

            @Override
            public void onFailure(Call<PhotoResponse> c, Throwable t) {
                if (isAdded()) photoMsg(ApiError.NETWORK, true);
            }
        });
    }

    private void removePhoto() {
        ApiClient.get(requireContext()).deletePhoto().enqueue(new Callback<PhotoResponse>() {
            @Override
            public void onResponse(Call<PhotoResponse> c, Response<PhotoResponse> r) {
                if (!isAdded()) return;
                if (r.isSuccessful()) {
                    clearPhoto();
                    photoMsg("Profile photo removed.", false);
                } else if (r.code() == 401) {
                    Session.expire(requireActivity(), true);
                } else {
                    photoMsg(ApiError.message(r), true);
                }
            }

            @Override
            public void onFailure(Call<PhotoResponse> c, Throwable t) {
                if (isAdded()) photoMsg(ApiError.NETWORK, true);
            }
        });
    }

    // ---------------- password ----------------

    private void msg(String text, boolean error) {
        tvMessage.setTextColor(error ? 0xFFFF6B6B : 0xFF3DDC84);
        tvMessage.setText(text);
        tvMessage.setVisibility(View.VISIBLE);
    }

    private void submit() {
        String cur = etCurrent.getText().toString();
        String nw = etNew.getText().toString();
        String cf = etConfirm.getText().toString();
        if (cur.isEmpty()) { msg("Enter your current password.", true); return; }
        if (nw.length() < 8) { msg("The new password must be at least 8 characters.", true); return; }
        if (!nw.equals(cf)) { msg("The new passwords do not match.", true); return; }
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.currentPassword = cur;
        req.newPassword = nw;
        btn.setEnabled(false);
        ApiClient.get(requireContext()).changePassword(req).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (!isAdded()) return;
                btn.setEnabled(true);
                if (r.isSuccessful()) {
                    etCurrent.setText("");
                    etNew.setText("");
                    etConfirm.setText("");
                    msg("Password changed. Use the new password next time you sign in.", false);
                } else if (r.code() == 401) {
                    Session.expire(requireActivity(), true);
                } else {
                    msg(ApiError.message(r), true);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (!isAdded()) return;
                btn.setEnabled(true);
                msg(ApiError.NETWORK, true);
            }
        });
    }
}
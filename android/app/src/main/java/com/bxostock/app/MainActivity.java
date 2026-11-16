package com.bxostock.app;

import com.bxostock.app.util.BxoDialog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.PhotoResponse;
import com.bxostock.app.util.Session;
import com.bxostock.app.util.TokenStore;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private TextView tvTitle;
    private DrawerLayout drawer;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback netCallback;
    private Snackbar offlineBar;
    private boolean wasOffline = false;
    private final Handler netHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!TokenStore.isLoggedIn(this)) {
            Session.expire(this, false);
            return;
        }
        setContentView(R.layout.activity_main);
        tvTitle = findViewById(R.id.tvTitle);
        drawer = findViewById(R.id.drawer);

        String name = TokenStore.name(this);
        String role = TokenStore.role(this);
        ((TextView) findViewById(R.id.tvDrawerUser)).setText(name);
        String initial = name == null ? "" : name.trim();
        ((TextView) findViewById(R.id.tvDrawerInitial)).setText(initial.isEmpty() ? "?" : initial.substring(0, 1).toUpperCase());
        // name + profile photo sit above the Log out button; tapping them opens the profile
        findViewById(R.id.dMe).setOnClickListener(v -> go(new ProfileFragment()));
        loadAvatar();
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                loadAvatar();
            }
        });

        findViewById(R.id.btnMenu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            drawer.closeDrawers();
            confirmLogout();
        });

        findViewById(R.id.dProfile).setOnClickListener(v -> go(new ProfileFragment()));
        findViewById(R.id.dOrders).setOnClickListener(v -> go(new OrdersFragment()));
        findViewById(R.id.dReturns).setOnClickListener(v -> go(new ReturnsFragment()));
        findViewById(R.id.dCustomers).setOnClickListener(v -> go(new CustomersFragment()));
        findViewById(R.id.dProducts).setOnClickListener(v -> go(new ProductsFragment()));
        findViewById(R.id.dStock).setOnClickListener(v -> go(new StockFragment()));
        findViewById(R.id.dStockHistory).setOnClickListener(v -> go(new StockHistoryFragment()));

        setupGroup(R.id.gSales, R.id.bSales, R.id.aSales);
        setupGroup(R.id.gInventory, R.id.bInventory, R.id.aInventory);

        if ("ADMIN".equals(role)) {
            findViewById(R.id.adminGroup).setVisibility(View.VISIBLE);
            findViewById(R.id.dCategories).setVisibility(View.VISIBLE);
            findViewById(R.id.dSuppliers).setVisibility(View.VISIBLE);
            findViewById(R.id.dPurchases).setVisibility(View.VISIBLE);
            setupGroup(R.id.gAdmin, R.id.bAdmin, R.id.aAdmin);
            findViewById(R.id.dUsers).setOnClickListener(v -> go(new UsersFragment()));
            findViewById(R.id.dCategories).setOnClickListener(v -> go(new CategoriesFragment()));
            findViewById(R.id.dReports).setOnClickListener(v -> go(new ReportsFragment()));
            findViewById(R.id.dAudit).setOnClickListener(v -> go(new AuditLogsFragment()));
            findViewById(R.id.dStaff).setOnClickListener(v -> go(new StaffPerformanceFragment()));
            findViewById(R.id.dSuppliers).setOnClickListener(v -> go(new SuppliersFragment()));
            findViewById(R.id.dPurchases).setOnClickListener(v -> go(new PurchasesFragment()));
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawers();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.container);
            if (f != null) tvTitle.setText(titleFor(f));
        });

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
        if (savedInstanceState == null) nav.setSelectedItemId(R.id.nav_home);
        watchNetwork();
    }

    /** Show the user's profile photo next to their name (falls back to the first letter). */
    private void loadAvatar() {
        ApiClient.get(this).getPhoto().enqueue(new Callback<PhotoResponse>() {
            @Override
            public void onResponse(Call<PhotoResponse> c, Response<PhotoResponse> r) {
                if (isFinishing() || !r.isSuccessful() || r.body() == null) return;
                String photo = r.body().photo;
                ImageView iv = findViewById(R.id.ivDrawerAvatar);
                TextView tv = findViewById(R.id.tvDrawerInitial);
                Bitmap bm = null;
                if (photo != null && !photo.isEmpty()) {
                    try {
                        String b64 = photo.contains(",") ? photo.substring(photo.indexOf(',') + 1) : photo;
                        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                        bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    } catch (Exception ignored) { }
                }
                if (bm != null) {
                    iv.setImageBitmap(bm);
                    iv.setVisibility(View.VISIBLE);
                    tv.setVisibility(View.GONE);
                } else {
                    iv.setVisibility(View.GONE);
                    tv.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<PhotoResponse> c, Throwable t) { }
        });
    }

    /** Tap the header to open or close a group; the arrow turns. */
    private void setupGroup(int headerId, int bodyId, int arrowId) {
        final View body = findViewById(bodyId);
        final TextView arrow = findViewById(arrowId);
        findViewById(headerId).setOnClickListener(v -> {
            boolean open = body.getVisibility() == View.VISIBLE;
            body.setVisibility(open ? View.GONE : View.VISIBLE);
            arrow.animate().rotation(open ? 0f : 180f).setDuration(200).start();
        });
    }

    /** Shows a red bar when the internet is lost and a green one when it returns. */
    private void watchNetwork() {
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        if (cm.getActiveNetwork() == null) showOffline();
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> showOnline());
            }

            @Override
            public void onLost(Network network) {
                // wait a moment: switching WiFi to mobile data also fires onLost
                netHandler.postDelayed(() -> {
                    if (cm.getActiveNetwork() == null) showOffline();
                }, 700);
            }
        };
        cm.registerDefaultNetworkCallback(netCallback);
    }

    private void showOffline() {
        if (isFinishing() || wasOffline) return;
        wasOffline = true;
        offlineBar = Snackbar.make(findViewById(android.R.id.content),
                "You are offline. Check your internet connection.", Snackbar.LENGTH_INDEFINITE);
        offlineBar.setBackgroundTint(0xFFDC2626).setTextColor(0xFFFFFFFF);
        offlineBar.setAnchorView(findViewById(R.id.bottomNav));
        offlineBar.show();
    }

    private void showOnline() {
        if (!wasOffline || isFinishing()) return;
        wasOffline = false;
        if (offlineBar != null) offlineBar.dismiss();
        Snackbar ok = Snackbar.make(findViewById(android.R.id.content), "Back online", Snackbar.LENGTH_SHORT);
        ok.setBackgroundTint(0xFF16A34A).setTextColor(0xFFFFFFFF);
        ok.setAnchorView(findViewById(R.id.bottomNav));
        ok.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        netHandler.removeCallbacksAndMessages(null);
        if (cm != null && netCallback != null) {
            try {
                cm.unregisterNetworkCallback(netCallback);
            } catch (Exception ignored) {
            }
        }
    }

    private void go(Fragment f) {
        drawer.closeDrawers();
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        open(f);
    }

    private String titleFor(Fragment f) {
        if (f instanceof ProductsFragment) return "Products";
        if (f instanceof StockFragment) return "Stock in / out";
        if (f instanceof StockHistoryFragment) return "Stock history";
        if (f instanceof CustomersFragment) return "Customers";
        if (f instanceof OrdersFragment) return "Orders";
        if (f instanceof UsersFragment) return "Users";
        if (f instanceof CategoriesFragment) return "Categories";
        if (f instanceof ReportsFragment) return "Reports";
        if (f instanceof AuditLogsFragment) return "Audit logs";
        if (f instanceof StaffPerformanceFragment) return "Staff performance";
        if (f instanceof ReturnsFragment) return "Returns";
        if (f instanceof SuppliersFragment) return "Suppliers";
        if (f instanceof PurchasesFragment) return "Purchase orders";
        if (f instanceof ProfileFragment) return "Profile";
        return "Dashboard";
    }

    /** Opens a screen on top of the current one; the back button returns. */
    public void open(Fragment f) {
        tvTitle.setText(titleFor(f));
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, f).addToBackStack(null).commit();
    }

    private void show(int id) {
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        Fragment f;
        if (id == R.id.nav_products) f = new ProductsFragment();
        else if (id == R.id.nav_stock) f = new StockFragment();
        else if (id == R.id.nav_customers) f = new CustomersFragment();
        else if (id == R.id.nav_orders) f = new OrdersFragment();
        else f = new DashboardFragment();
        tvTitle.setText(titleFor(f));
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
    }

    private void confirmLogout() {
        new BxoDialog(this)
                .setTitle("Log out?")
                .setMessage("You will need to sign in again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (d, w) -> logout())
                .show();
    }

    private void logout() {
        ApiClient.get(this).logout().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> r) {
                Session.expire(MainActivity.this, false);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Session.expire(MainActivity.this, false);
            }
        });
    }
}
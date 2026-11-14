package com.bxostock.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.util.Session;
import com.bxostock.app.util.TokenStore;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private TextView tvTitle;
    private DrawerLayout drawer;

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
        ((TextView) findViewById(R.id.tvUser)).setText(name);
        ((TextView) findViewById(R.id.tvRole)).setText(role);
        ((TextView) findViewById(R.id.tvDrawerUser)).setText(name);
        ((TextView) findViewById(R.id.tvDrawerRole)).setText(role);

        findViewById(R.id.btnMenu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            drawer.closeDrawers();
            confirmLogout();
        });

        findViewById(R.id.dProfile).setOnClickListener(v -> go(new ProfileFragment()));
        findViewById(R.id.dReturns).setOnClickListener(v -> go(new ReturnsFragment()));

        if ("ADMIN".equals(role)) {
            findViewById(R.id.adminItems).setVisibility(View.VISIBLE);
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
    }

    private void go(Fragment f) {
        drawer.closeDrawers();
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        open(f);
    }

    private String titleFor(Fragment f) {
        if (f instanceof ProductsFragment) return "Products";
        if (f instanceof StockFragment) return "Stock in / out";
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
        new MaterialAlertDialogBuilder(this)
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
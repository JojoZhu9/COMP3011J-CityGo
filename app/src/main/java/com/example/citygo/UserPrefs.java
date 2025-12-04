package com.example.citygo;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class UserPrefs {
    private static final String PREFS = "CityGoPrefs";
    public static final String K_ONBOARDING = "is_onboarding_complete";
    public static final String K_NAME = "user_name";
    public static final String K_EMAIL = "user_email";
    public static final String K_HOME_CITY = "user_home_city";
    public static final String K_BUDGET_CENTS = "daily_budget_cents";
    public static final String K_INTERESTS = "user_preferences";
    public static final String K_DIETARY = "dietary_preferences";

    private final SharedPreferences sp;

    public UserPrefs(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isOnboardingComplete() { return sp.getBoolean(K_ONBOARDING, false); }
    public void setOnboardingComplete(boolean v) { sp.edit().putBoolean(K_ONBOARDING, v).apply(); }

    public String getName() { return sp.getString(K_NAME, ""); }
    public void setName(String v) { sp.edit().putString(K_NAME, v == null ? "" : v).apply(); }

    public String getEmail() { return sp.getString(K_EMAIL, ""); }
    public void setEmail(String v) { sp.edit().putString(K_EMAIL, v == null ? "" : v).apply(); }

    public String getHomeCity() { return sp.getString(K_HOME_CITY, ""); }
    public void setHomeCity(String v) { sp.edit().putString(K_HOME_CITY, v == null ? "" : v).apply(); }

    public int getDailyBudgetCents() { return sp.getInt(K_BUDGET_CENTS, 0); }
    public void setDailyBudgetCents(int cents) { sp.edit().putInt(K_BUDGET_CENTS, Math.max(0, cents)).apply(); }

    public Set<String> getInterests() {
        return new HashSet<>(sp.getStringSet(K_INTERESTS, Collections.emptySet()));
    }
    public void setInterests(Set<String> interests) {
        sp.edit().putStringSet(K_INTERESTS, interests == null ? new HashSet<>() : interests).apply();
    }

    public Set<String> getDietary() {
        return new HashSet<>(sp.getStringSet(K_DIETARY, Collections.emptySet()));
    }
    public void setDietary(Set<String> dietary) {
        sp.edit().putStringSet(K_DIETARY, dietary == null ? new HashSet<>() : dietary).apply();
    }
}

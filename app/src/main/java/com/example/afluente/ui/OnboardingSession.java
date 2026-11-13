package com.example.afluente.ui;

/** Rascunho temporário em memória. A senha nunca é gravada nas preferências ou no SQLite. */
public final class OnboardingSession {
    public static String name, email, password, createdUid;
    public static int residents;
    public static String residenceType, waterEstimate, energyEstimate;
    private OnboardingSession() { }
    public static void clear() {
        name = email = password = createdUid = null;
        residenceType = waterEstimate = energyEstimate = null;
        residents = 0;
    }
}

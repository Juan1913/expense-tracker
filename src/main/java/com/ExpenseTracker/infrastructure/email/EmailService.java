package com.ExpenseTracker.infrastructure.email;

public interface EmailService {
    void sendVerificationEmail(String to, String verificationToken, String frontendUrl);

    void sendPasswordResetEmail(String to, String resetToken, String frontendUrl);
}

package com.ExpenseTracker.infrastructure.email;

public interface EmailService {
    void sendVerificationEmail(String to, String verificationToken, String frontendUrl);
}

package com.leonardtrinh.supportsaas.billing;

public record InvoiceResponse(
        String id,
        String date,
        String description,
        double amount,
        String status,
        String pdfUrl) {}

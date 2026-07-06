package com.oac.sample.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Order domain model.
 * Contains customer PII fields that demonstrate field-level entitlement masking.
 */
@Document(collection = "orders")
public class Order {

    @Id
    private String id;
    private String customerName;
    private String customerEmail;
    private String customerSsn;
    private String customerPhone;
    private String product;
    private int quantity;
    private double total;
    private String ownerId;
    private String status;

    public Order() {}

    public Order(String id, String customerName, String customerEmail,
                 String customerSsn, String product, int quantity,
                 double total, String ownerId) {
        this.id = id;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerSsn = customerSsn;
        this.product = product;
        this.quantity = quantity;
        this.total = total;
        this.ownerId = ownerId;
        this.status = "CREATED";
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerSsn() { return customerSsn; }
    public void setCustomerSsn(String customerSsn) { this.customerSsn = customerSsn; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
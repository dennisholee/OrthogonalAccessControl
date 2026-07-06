package com.oac.example.api.generated.model;

public class OrderResponse {
    private String id;
    private String product;
    private Integer quantity;
    private Double total;
    private String status;
    private String customerName;
    private String customerEmail;
    private String customerSsn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerSsn() { return customerSsn; }
    public void setCustomerSsn(String customerSsn) { this.customerSsn = customerSsn; }
}
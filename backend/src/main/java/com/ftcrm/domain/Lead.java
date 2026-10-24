package com.ftcrm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "lead_record")
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false, length = 80)
    private String country;

    @Column(name = "contact_name", nullable = false, length = 80)
    private String contactName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeadStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_grade", length = 8)
    private CustomerGrade customerGrade;

    @Column(name = "product_interest", length = 200)
    private String productInterest;

    @Column(length = 80)
    private String quantity;

    @Column(name = "destination_port", length = 120)
    private String destinationPort;

    @Column(length = 200)
    private String quotation;

    @Column(name = "customer_reply", length = 2000)
    private String customerReply;

    @Column(name = "order_or_contract_number", length = 120)
    private String orderOrContractNumber;

    @Column(name = "shipping_document_number", length = 120)
    private String shippingDocumentNumber;

    @Column(name = "receipt_or_arrival_note", length = 500)
    private String receiptOrArrivalNote;

    @Column(length = 200)
    private String budget;

    @Column(name = "delivery_date", length = 120)
    private String deliveryDate;

    @Column(name = "days_since_valid_interaction", nullable = false)
    private int daysSinceValidInteraction;

    @Column(name = "consecutive_unanswered", nullable = false)
    private int consecutiveUnanswered;

    @Column(name = "days_since_last_deal")
    private Integer daysSinceLastDeal;

    @Column(name = "wake_plan_json", length = 2000)
    private String wakePlanJson;

    @Column(length = 80)
    private String source;

    @Column(length = 2000)
    private String notes;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public LeadStage getStage() {
        return stage;
    }

    public void setStage(LeadStage stage) {
        this.stage = stage;
    }

    public CustomerGrade getCustomerGrade() {
        return customerGrade;
    }

    public void setCustomerGrade(CustomerGrade customerGrade) {
        this.customerGrade = customerGrade;
    }

    public String getProductInterest() {
        return productInterest;
    }

    public void setProductInterest(String productInterest) {
        this.productInterest = productInterest;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(String destinationPort) {
        this.destinationPort = destinationPort;
    }

    public String getQuotation() {
        return quotation;
    }

    public void setQuotation(String quotation) {
        this.quotation = quotation;
    }

    public String getCustomerReply() {
        return customerReply;
    }

    public void setCustomerReply(String customerReply) {
        this.customerReply = customerReply;
    }

    public String getOrderOrContractNumber() {
        return orderOrContractNumber;
    }

    public void setOrderOrContractNumber(String orderOrContractNumber) {
        this.orderOrContractNumber = orderOrContractNumber;
    }

    public String getShippingDocumentNumber() {
        return shippingDocumentNumber;
    }

    public void setShippingDocumentNumber(String shippingDocumentNumber) {
        this.shippingDocumentNumber = shippingDocumentNumber;
    }

    public String getReceiptOrArrivalNote() {
        return receiptOrArrivalNote;
    }

    public void setReceiptOrArrivalNote(String receiptOrArrivalNote) {
        this.receiptOrArrivalNote = receiptOrArrivalNote;
    }

    public String getBudget() {
        return budget;
    }

    public void setBudget(String budget) {
        this.budget = budget;
    }

    public String getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(String deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public int getDaysSinceValidInteraction() {
        return daysSinceValidInteraction;
    }

    public void setDaysSinceValidInteraction(int daysSinceValidInteraction) {
        this.daysSinceValidInteraction = daysSinceValidInteraction;
    }

    public int getConsecutiveUnanswered() {
        return consecutiveUnanswered;
    }

    public void setConsecutiveUnanswered(int consecutiveUnanswered) {
        this.consecutiveUnanswered = consecutiveUnanswered;
    }

    public Integer getDaysSinceLastDeal() {
        return daysSinceLastDeal;
    }

    public void setDaysSinceLastDeal(Integer daysSinceLastDeal) {
        this.daysSinceLastDeal = daysSinceLastDeal;
    }

    public String getWakePlanJson() {
        return wakePlanJson;
    }

    public void setWakePlanJson(String wakePlanJson) {
        this.wakePlanJson = wakePlanJson;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

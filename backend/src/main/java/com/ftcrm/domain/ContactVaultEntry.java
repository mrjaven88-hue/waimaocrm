package com.ftcrm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "contact_vault")
public class ContactVaultEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lead_id", nullable = false, unique = true)
    private Long leadId;

    @Column(name = "phone_cipher", length = 2000)
    private String phoneCipher;

    @Column(name = "phone_iv", length = 64)
    private String phoneIv;

    @Column(name = "email_cipher", length = 2000)
    private String emailCipher;

    @Column(name = "email_iv", length = 64)
    private String emailIv;

    @Column(name = "whatsapp_cipher", length = 2000)
    private String whatsappCipher;

    @Column(name = "whatsapp_iv", length = 64)
    private String whatsappIv;

    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "whatsapp_hash", length = 64)
    private String whatsappHash;

    @Column(name = "phone_masked", length = 80)
    private String phoneMasked;

    @Column(name = "email_masked", length = 160)
    private String emailMasked;

    @Column(name = "whatsapp_masked", length = 80)
    private String whatsappMasked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getLeadId() {
        return leadId;
    }

    public void setLeadId(Long leadId) {
        this.leadId = leadId;
    }

    public String getPhoneCipher() {
        return phoneCipher;
    }

    public void setPhoneCipher(String phoneCipher) {
        this.phoneCipher = phoneCipher;
    }

    public String getPhoneIv() {
        return phoneIv;
    }

    public void setPhoneIv(String phoneIv) {
        this.phoneIv = phoneIv;
    }

    public String getEmailCipher() {
        return emailCipher;
    }

    public void setEmailCipher(String emailCipher) {
        this.emailCipher = emailCipher;
    }

    public String getEmailIv() {
        return emailIv;
    }

    public void setEmailIv(String emailIv) {
        this.emailIv = emailIv;
    }

    public String getWhatsappCipher() {
        return whatsappCipher;
    }

    public void setWhatsappCipher(String whatsappCipher) {
        this.whatsappCipher = whatsappCipher;
    }

    public String getWhatsappIv() {
        return whatsappIv;
    }

    public void setWhatsappIv(String whatsappIv) {
        this.whatsappIv = whatsappIv;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public void setEmailHash(String emailHash) {
        this.emailHash = emailHash;
    }

    public String getWhatsappHash() {
        return whatsappHash;
    }

    public void setWhatsappHash(String whatsappHash) {
        this.whatsappHash = whatsappHash;
    }

    public String getPhoneMasked() {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked) {
        this.phoneMasked = phoneMasked;
    }

    public String getEmailMasked() {
        return emailMasked;
    }

    public void setEmailMasked(String emailMasked) {
        this.emailMasked = emailMasked;
    }

    public String getWhatsappMasked() {
        return whatsappMasked;
    }

    public void setWhatsappMasked(String whatsappMasked) {
        this.whatsappMasked = whatsappMasked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

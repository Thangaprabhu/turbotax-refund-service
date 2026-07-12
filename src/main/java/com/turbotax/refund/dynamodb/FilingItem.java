package com.turbotax.refund.dynamodb;

import com.turbotax.refund.domain.enums.FormType;
import com.turbotax.refund.domain.enums.IrsStatus;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * DynamoDB item for filings table.
 * PK: taxpayerId
 * SK: taxYear#formType#jurisdiction  (e.g. "2024#F1040#FEDERAL")
 */
@DynamoDbBean
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FilingItem {

    private String taxpayerId;          // PK
    private String sk;                  // SortKey, SK: taxYear#formType#jurisdiction

    private String taxYear;
    private String formType;            // FormType enum name
    private String jurisdiction;        // FEDERAL | CA | NY | etc.
    private String irsStatus;           // IrsStatus enum name
    private String refundAmountEncrypted;
    private String filingDate;
    private String expectedDepositDate;
    private String submissionId;        // IRS MeF submission ID
    private String adapterUsed;         // IRS_IMF | IRS_BMF
    private Integer aiPredictedDays;
    private Double aiConfidence;
    private String aiModelVersion;
    private String lastSyncedAt;
    private List<StatusHistoryEntry> statusHistory = new ArrayList<>();

    @DynamoDbPartitionKey
    public String getTaxpayerId() { return taxpayerId; }

    @DynamoDbSortKey
    public String getSk() { return sk; }

    public static String buildSk(String taxYear, FormType formType, String jurisdiction) {
        return taxYear + "#" + formType.name() + "#" + jurisdiction.toUpperCase();
    }

    @DynamoDbBean
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatusHistoryEntry {
        private String status;
        private String timestamp;
        private String source;
    }
}

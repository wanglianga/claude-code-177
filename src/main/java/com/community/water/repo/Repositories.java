package com.community.water.repo;

import com.community.water.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface Repositories {

    interface CommunityRepository extends JpaRepository<Community, Long> {
    }

    interface DeviceRepository extends JpaRepository<Device, Long> {
        Optional<Device> findByDeviceCode(String deviceCode);

        List<Device> findByCommunityId(Long communityId);

        List<Device> findByStatus(DeviceStatus status);
    }

    interface WaterFilterRepository extends JpaRepository<WaterFilter, Long> {
        Optional<WaterFilter> findByFilterSerialNo(String serialNo);

        Optional<WaterFilter> findFirstByDeviceIdAndStatusOrderByInstalledAtDesc(Long deviceId, FilterStatus status);

        List<WaterFilter> findByDeviceIdOrderByInstalledAtDesc(Long deviceId);
    }

    interface TelemetryRepository extends JpaRepository<TelemetryRecord, Long> {
        List<TelemetryRecord> findByDeviceIdOrderByEventTimeDesc(Long deviceId);

        Optional<TelemetryRecord> findFirstByDeviceIdOrderByEventTimeDesc(Long deviceId);
    }

    interface ResidentAccountRepository extends JpaRepository<ResidentAccount, Long> {
        Optional<ResidentAccount> findByAccountNo(String accountNo);

        List<ResidentAccount> findByCommunityIdAndBuilding(Long communityId, String building);
    }

    interface TechnicianRepository extends JpaRepository<Technician, Long> {
        List<Technician> findByCommunityId(Long communityId);
    }

    interface DispenseRepository extends JpaRepository<DispenseRecord, Long> {
        List<DispenseRecord> findByDeviceIdOrderByDispensedAtDesc(Long deviceId);

        List<DispenseRecord> findByAccountNoOrderByDispensedAtDesc(String accountNo);

        @Query("select coalesce(sum(d.liters),0) from DispenseRecord d where d.deviceId = :deviceId and d.dispensedAt >= :since")
        double sumLitersSince(@Param("deviceId") Long deviceId, @Param("since") OffsetDateTime since);

        long countByDeviceIdAndSmellComplaintTrueAndDispensedAtAfter(Long deviceId, OffsetDateTime since);
    }

    interface ComplaintRepository extends JpaRepository<Complaint, Long> {
        List<Complaint> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);

        List<Complaint> findByCaseId(Long caseId);

        long countByDeviceIdAndStatusAndCreatedAtAfter(Long deviceId, ComplaintStatus status, OffsetDateTime since);

        @Query("select c from Complaint c where c.deviceId = :deviceId and c.createdAt >= :since order by c.createdAt")
        List<Complaint> findRecent(@Param("deviceId") Long deviceId, @Param("since") OffsetDateTime since);
    }

    interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, Long> {
        Optional<MaintenanceTicket> findByTicketNo(String ticketNo);

        List<MaintenanceTicket> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);

        List<MaintenanceTicket> findByCaseId(Long caseId);

        List<MaintenanceTicket> findByTechnicianIdOrderByCreatedAtDesc(Long technicianId);

        List<MaintenanceTicket> findByStatusIn(List<TicketStatus> statuses);

        @Query("select t from MaintenanceTicket t where t.status in :statuses and t.dueAt < :now and t.lateAlerted = false")
        List<MaintenanceTicket> findOverdue(@Param("statuses") List<TicketStatus> statuses,
                                            @Param("now") OffsetDateTime now);
    }

    interface FilterReplacementRepository extends JpaRepository<FilterReplacement, Long> {
        List<FilterReplacement> findByDeviceIdOrderByReplacedAtDesc(Long deviceId);

        List<FilterReplacement> findByCaseId(Long caseId);

        Optional<FilterReplacement> findByTicketId(Long ticketId);
    }

    interface RetestRepository extends JpaRepository<WaterQualityRetest, Long> {
        List<WaterQualityRetest> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);
    }

    interface ChargeRepository extends JpaRepository<Charge, Long> {
        Optional<Charge> findByChargeNo(String chargeNo);

        List<Charge> findByCaseId(Long caseId);

        List<Charge> findByStatus(ChargeStatus status);

        List<Charge> findByCommunityIdOrderByCreatedAtDesc(Long communityId);

        List<Charge> findByAccountNoOrderByCreatedAtDesc(String accountNo);

        List<Charge> findByCommunityIdAndBuildingAndType(Long communityId, String building, ChargeType type);
    }

    interface InvoiceRepository extends JpaRepository<Invoice, Long> {
        Optional<Invoice> findByInvoiceNo(String invoiceNo);

        List<Invoice> findByChargeId(Long chargeId);
    }

    interface AlertRepository extends JpaRepository<Alert, Long> {
        List<Alert> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);

        List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

        List<Alert> findByCaseId(Long caseId);

        List<Alert> findByDeviceIdAndStatus(Long deviceId, AlertStatus status);

        List<Alert> findByCommunityIdAndStatusOrderByCreatedAtDesc(Long communityId, AlertStatus status);
    }

    interface FulfillmentCaseRepository extends JpaRepository<FulfillmentCase, Long> {
        Optional<FulfillmentCase> findByCaseNo(String caseNo);

        List<FulfillmentCase> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);

        List<FulfillmentCase> findByCommunityIdOrderByCreatedAtDesc(Long communityId);

        List<FulfillmentCase> findByStageNot(String stage);
    }

    interface NotificationRepository extends JpaRepository<Notification, Long> {
        List<Notification> findByCommunityIdOrderByCreatedAtDesc(Long communityId);

        List<Notification> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);
    }

    interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
        List<OutboxEvent> findFirst100ByPublishedFalseOrderByIdAsc();
    }
}

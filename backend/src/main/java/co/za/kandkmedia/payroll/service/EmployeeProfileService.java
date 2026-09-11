package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.dto.EmployeeProfileDto;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Applies only the non-null fields from an EmployeeProfileDto, so a person
 * filling in their profile a section at a time never wipes out fields
 * they haven't gotten to yet.
 */
@Service
@RequiredArgsConstructor
public class EmployeeProfileService {

    private final EmployeeRepository employeeRepository;

    public Employee updateProfile(Long employeeId, EmployeeProfileDto dto) {
        Employee e = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));

        if (dto.getTitle() != null) e.setTitle(dto.getTitle());
        if (dto.getFirstName() != null) e.setFirstName(dto.getFirstName());
        if (dto.getSecondName() != null) e.setSecondName(dto.getSecondName());
        if (dto.getLastName() != null) e.setLastName(dto.getLastName());
        if (dto.getInitials() != null) e.setInitials(dto.getInitials());
        if (dto.getDateOfBirth() != null) e.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getIdNumber() != null) e.setIdNumber(dto.getIdNumber());
        if (dto.getPassportNumber() != null) e.setPassportNumber(dto.getPassportNumber());
        if (dto.getPassportCountry() != null) e.setPassportCountry(dto.getPassportCountry());
        if (dto.getRace() != null) e.setRace(dto.getRace());
        if (dto.getRelationshipStatus() != null) e.setRelationshipStatus(dto.getRelationshipStatus());

        if (dto.getContactTelephone() != null) e.setContactTelephone(dto.getContactTelephone());
        if (dto.getContactCellphone() != null) e.setContactCellphone(dto.getContactCellphone());
        if (dto.getEmergencyContactName() != null) e.setEmergencyContactName(dto.getEmergencyContactName());
        if (dto.getEmergencyContactTelephone() != null) e.setEmergencyContactTelephone(dto.getEmergencyContactTelephone());
        if (dto.getEmergencyContactCellphone() != null) e.setEmergencyContactCellphone(dto.getEmergencyContactCellphone());

        if (dto.getTaxOffice() != null) e.setTaxOffice(dto.getTaxOffice());
        if (dto.getIncomeTaxNumber() != null) e.setIncomeTaxNumber(dto.getIncomeTaxNumber());

        if (dto.getBankAccountType() != null) e.setBankAccountType(dto.getBankAccountType());
        if (dto.getBankBranchCode() != null) e.setBankBranchCode(dto.getBankBranchCode());
        if (dto.getBankName() != null) e.setBankName(dto.getBankName());
        if (dto.getBankBranchName() != null) e.setBankBranchName(dto.getBankBranchName());
        if (dto.getBankAccountNumber() != null) e.setBankAccountNumber(dto.getBankAccountNumber());
        if (dto.getBankAccountHolder() != null) e.setBankAccountHolder(dto.getBankAccountHolder());
        if (dto.getBankAccountRelationship() != null) e.setBankAccountRelationship(dto.getBankAccountRelationship());

        if (dto.getResUnitNumber() != null) e.setResUnitNumber(dto.getResUnitNumber());
        if (dto.getResComplexName() != null) e.setResComplexName(dto.getResComplexName());
        if (dto.getResStreetNumber() != null) e.setResStreetNumber(dto.getResStreetNumber());
        if (dto.getResStreetName() != null) e.setResStreetName(dto.getResStreetName());
        if (dto.getResSuburb() != null) e.setResSuburb(dto.getResSuburb());
        if (dto.getResCity() != null) e.setResCity(dto.getResCity());
        if (dto.getResPostalCode() != null) e.setResPostalCode(dto.getResPostalCode());

        if (dto.getPostalService() != null) e.setPostalService(dto.getPostalService());
        if (dto.getPostalNumber() != null) e.setPostalNumber(dto.getPostalNumber());
        if (dto.getPostStreetNumber() != null) e.setPostStreetNumber(dto.getPostStreetNumber());
        if (dto.getPostStreetName() != null) e.setPostStreetName(dto.getPostStreetName());
        if (dto.getPostSuburb() != null) e.setPostSuburb(dto.getPostSuburb());
        if (dto.getPostCity() != null) e.setPostCity(dto.getPostCity());
        if (dto.getPostPostalCode() != null) e.setPostPostalCode(dto.getPostPostalCode());

        if (dto.getPhone() != null) e.setPhone(dto.getPhone());
        if (dto.getPosition() != null) e.setPosition(dto.getPosition());
        if (dto.getEmploymentType() != null) e.setEmploymentType(dto.getEmploymentType());
        if (dto.getRateType() != null) e.setRateType(dto.getRateType());

        return employeeRepository.save(e);
    }
}

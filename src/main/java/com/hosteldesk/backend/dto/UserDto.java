package com.hosteldesk.backend.dto;

import com.hosteldesk.backend.entity.AccountStatus;
import com.hosteldesk.backend.entity.Role;
import com.hosteldesk.backend.entity.User;

public class UserDto {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String institutionalId;
    private Role role;
    private AccountStatus status;
    private Long hostelId;
    private String hostelName;
    private Long departmentId;
    private String departmentName;
    private String roomNumber;

    public UserDto() {}

    public static UserDto fromEntity(User user) {
        if (user == null) return null;
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setInstitutionalId(user.getInstitutionalId());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setRoomNumber(user.getRoomNumber());
        if (user.getHostel() != null) {
            dto.setHostelId(user.getHostel().getId());
            dto.setHostelName(user.getHostel().getName());
        }
        if (user.getDepartment() != null) {
            dto.setDepartmentId(user.getDepartment().getId());
            dto.setDepartmentName(user.getDepartment().getName());
        }
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getInstitutionalId() { return institutionalId; }
    public void setInstitutionalId(String institutionalId) { this.institutionalId = institutionalId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public Long getHostelId() { return hostelId; }
    public void setHostelId(Long hostelId) { this.hostelId = hostelId; }

    public String getHostelName() { return hostelName; }
    public void setHostelName(String hostelName) { this.hostelName = hostelName; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
}

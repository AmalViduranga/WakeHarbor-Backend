package io.wakeharbor.control.repository;

import io.wakeharbor.control.domain.ApplicationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, String> {
}

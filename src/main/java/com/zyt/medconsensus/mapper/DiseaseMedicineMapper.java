package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.DiseaseMedicine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiseaseMedicineMapper extends JpaRepository<DiseaseMedicine, Long> {

    @Query(value = """
            select *
            from disease_medicine
            where disease_name ilike concat('%', :keyword, '%')
               or :keyword ilike concat('%', disease_name, '%')
            order by disease_name asc, medicine_name asc
            limit :limit
            """, nativeQuery = true)
    List<DiseaseMedicine> findByDiseaseKeyword(
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );
}

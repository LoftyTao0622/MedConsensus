package com.zyt.medconsensus.controller;

import com.zyt.medconsensus.service.ReportFollowUpService;
import com.zyt.medconsensus.service.ReportFollowUpService.FollowUp;
import com.zyt.medconsensus.service.ReportFollowUpService.Plan;
import com.zyt.medconsensus.service.ReportFollowUpService.Result;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReportFollowUpController {
    private final ReportFollowUpService followUps;
    public ReportFollowUpController(ReportFollowUpService followUps) {
        this.followUps = followUps;
    }

    @PostMapping("/workspace/diagnosis-records/{id}/publish-with-follow-up")
    public void publish(Principal doctor, @PathVariable long id, @Valid @RequestBody Plan plan) {
        followUps.publishWithPlan(Long.parseLong(doctor.getName()), id, plan);
    }

    @GetMapping("/workspace/follow-ups")
    public List<FollowUp> doctorTasks(Principal doctor) {
        return followUps.doctorTasks(Long.parseLong(doctor.getName()));
    }

    @GetMapping("/patient/follow-ups")
    public List<FollowUp> patientTasks(Principal patient) {
        return followUps.patientTasks(Long.parseLong(patient.getName()));
    }

    @PostMapping("/patient/follow-ups/{id}/result")
    public void submit(Principal patient, @PathVariable long id, @Valid @RequestBody Result result) {
        followUps.submit(Long.parseLong(patient.getName()), id, result);
    }

    @PostMapping("/workspace/follow-ups/{id}/complete")
    public void complete(Principal doctor, @PathVariable long id) {
        followUps.complete(Long.parseLong(doctor.getName()), id);
    }
}

package com.zyt.medconsensus.controller;

import com.zyt.medconsensus.dto.EvidenceSource;
import com.zyt.medconsensus.service.EvidenceSourceService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceSourceController {
    private final EvidenceSourceService sources;

    public EvidenceSourceController(EvidenceSourceService sources) { this.sources = sources; }

    @GetMapping
    public List<EvidenceSource> list(@RequestParam String sessionId, HttpSession session) {
        Actor actor = account(session);
        return sources.list(actor.id(), actor.patient(), sessionId);
    }

    @GetMapping("/reports/{reportId}/revisions/{revision}")
    public List<EvidenceSource> report(@PathVariable long reportId, @PathVariable int revision, HttpSession session) {
        Actor actor = account(session);
        return sources.forRevision(actor.id(), actor.patient(), reportId, revision);
    }

    @GetMapping("/{id}")
    public EvidenceSource get(@PathVariable String id, HttpSession session) {
        Actor actor = account(session);
        return sources.find(actor.id(), actor.patient(), id);
    }

    @GetMapping("/{id}/original")
    public ResponseEntity<byte[]> original(@PathVariable String id, HttpSession session) {
        Actor actor = account(session);
        var file = sources.original(actor.id(), actor.patient(), id);
        ContentDisposition.Builder disposition = file.mediaType().endsWith(".document")
                ? ContentDisposition.attachment() : ContentDisposition.inline();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.mediaType())).body(file.bytes());
    }

    @PutMapping("/{id}/correction")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void correct(@PathVariable String id, @Valid @RequestBody Correction input, HttpSession session) {
        Actor actor = account(session);
        if (actor.patient()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要医生身份");
        sources.correctPatientUpload(actor.id(), id, input.text().trim());
    }

    private Actor account(HttpSession session) {
        Object id = session.getAttribute("CURRENT_USER_ID");
        Object role = session.getAttribute("CURRENT_USER_ROLE");
        if (id instanceof Long user && ("PATIENT".equals(role) || "DOCTOR".equals(role))) {
            return new Actor(user, "PATIENT".equals(role));
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    public record Correction(@NotBlank @Size(max = 12000) String text) {}
    private record Actor(long id, boolean patient) {}
}

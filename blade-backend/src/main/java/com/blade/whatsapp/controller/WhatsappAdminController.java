package com.blade.whatsapp.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.system.user.entity.User;
import com.blade.whatsapp.dto.WhatsappDtos.*;
import com.blade.whatsapp.service.WhatsappService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsappAdminController {
    private final WhatsappService service;

    @PostMapping("/collectors") @PreAuthorize("hasAuthority('btn:whatsapp:collector')")
    public R<CollectorCredential> create(@Valid @RequestBody CollectorCreateRequest body){return R.ok(service.createCollector(body,userId()));}
    @GetMapping("/accounts") @PreAuthorize("hasAuthority('menu:whatsapp')")
    public R<List<AccountView>> accounts(){return R.ok(service.accounts());}
    @GetMapping("/issues/summary") @PreAuthorize("hasAuthority('menu:whatsapp')")
    public R<IssueSummary> summary(){return R.ok(service.issueSummary());}
    @GetMapping("/issues") @PreAuthorize("hasAuthority('menu:whatsapp')")
    public R<PageResult<IssueView>> issues(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,
          @RequestParam(required=false)String status,@RequestParam(required=false)String mediaType,@RequestParam(required=false)Long accountId){
        return R.ok(service.issues(page,size,status,mediaType,accountId));}
    @PostMapping("/scan-jobs") @PreAuthorize("hasAuthority('btn:whatsapp:rescan')")
    public R<ScanJobView> scan(@RequestParam Long accountId){return R.ok(service.requestScan(accountId,userId()));}
    @GetMapping("/scan-jobs/latest") @PreAuthorize("hasAuthority('menu:whatsapp')")
    public R<ScanJobView> latest(){return R.ok(service.latestScan());}
    @GetMapping("/bindings/pending") @PreAuthorize("hasAuthority('menu:whatsapp')")
    public R<List<BindingView>> bindings(){return R.ok(service.pendingBindings());}
    @PutMapping("/bindings/{id}") @PreAuthorize("hasAuthority('btn:whatsapp:collector')")
    public R<Void> decide(@PathVariable Long id,@Valid @RequestBody BindingDecision body){service.decideBinding(id,body,userId());return R.ok();}

    private Long userId(){Authentication auth=SecurityContextHolder.getContext().getAuthentication();return auth!=null&&auth.getPrincipal() instanceof User u?u.getId():null;}
}

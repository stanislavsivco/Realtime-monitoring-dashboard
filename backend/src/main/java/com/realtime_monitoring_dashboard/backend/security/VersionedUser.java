package com.realtime_monitoring_dashboard.backend.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import lombok.Getter;

@Getter
public class VersionedUser extends User {

    private final int tokenVersion;

    public VersionedUser(String username, String password, Collection<? extends GrantedAuthority> authorities, int tokenVersion) {
        super(username, password, authorities);
        this.tokenVersion = tokenVersion;
    }
}

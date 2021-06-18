// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.connector.authentication;

import com.microsoft.bot.connector.Channels;
import com.microsoft.bot.connector.skills.BotFrameworkClient;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.RoleTypes;
import org.apache.commons.lang3.StringUtils;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ParameterizedBotFrameworkAuthentication extends BotFrameworkAuthentication {

    private Boolean validateAuthority;
    private String toChannelFromBotLoginUrl;
    private String toChannelFromBotOAuthScope;
    private String toBotFromChannelTokenIssuer;
    private String oAuthUrl;
    private String toBotFromChannelOpenIdMetadataUrl;
    private String toBotFromEmulatorOpenIdMetadataUrl;
    private String callerId;
    private ServiceClientCredentialsFactory credentialsFactory;
    private AuthenticationConfiguration authConfiguration;

    public ParameterizedBotFrameworkAuthentication(
        Boolean withValidateAuthority,
        String withToChannelFromBotLoginUrl,
        String withToChannelFromBotOAuthScope,
        String withToBotFromChannelTokenIssuer,
        String withOAuthUrl,
        String withToBotFromChannelOpenIdMetadataUrl,
        String withToBotFromEmulatorOpenIdMetadataUrl,
        String withCallerId,
        ServiceClientCredentialsFactory withCredentialsFactory,
        AuthenticationConfiguration withAuthConfiguration
    ) {
        this.validateAuthority = withValidateAuthority;
        this.toChannelFromBotLoginUrl = withToChannelFromBotLoginUrl;
        this.toChannelFromBotOAuthScope = withToChannelFromBotOAuthScope;
        this.toBotFromChannelTokenIssuer = withToBotFromChannelTokenIssuer;
        this.oAuthUrl = withOAuthUrl;
        this.toBotFromChannelOpenIdMetadataUrl = withToBotFromChannelOpenIdMetadataUrl;
        this.toBotFromEmulatorOpenIdMetadataUrl = withToBotFromEmulatorOpenIdMetadataUrl;
        this.callerId = withCallerId;
        this.credentialsFactory = withCredentialsFactory;
        this.authConfiguration = withAuthConfiguration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOriginatingAudience() {
        return this.toChannelFromBotOAuthScope;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<ClaimsIdentity> authenticateChannelRequest(String authHeader) {
        if (StringUtils.isBlank(authHeader.trim())) {
            return this.credentialsFactory.isAuthenticationDisabled().thenApply(isAuthDisabled -> {
               if (!isAuthDisabled) {
                   throw new AuthenticationException("Unauthorized Access. Request is not authorized");
               }

                // In the scenario where auth is disabled, we still want to have the isAuthenticated flag set in the
                // ClaimsIdentity. To do this requires adding in an empty claim. Since ChannelServiceHandler calls are
                // always a skill callback call, we set the skill claim too.
                return SkillValidation.createAnonymousSkillClaim();
            });
        }

        return jwtTokenValidation_ValidateAuthHeader(authHeader, "unknown", null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<AuthenticateRequestResult> authenticateRequest(Activity activity, String authHeader) {
        return jwtTokenValidation_AuthenticateRequest(activity, authHeader).thenCompose(claimsIdentity -> {
                String outboundAudience = SkillValidation.isSkillClaim(claimsIdentity.claims()) ?
                                          JwtTokenValidation.getAppIdFromClaims(claimsIdentity.claims()) :
                                          this.toChannelFromBotOAuthScope;

                return generateCallerId(this.credentialsFactory, claimsIdentity, this.callerId).thenCompose(resultCallerId -> {
                        ConnectorFactoryImpl connectorFactory = new ConnectorFactoryImpl(
                            getAppId(claimsIdentity),
                            this.toChannelFromBotOAuthScope,
                            this.toChannelFromBotLoginUrl,
                            this.validateAuthority,
                            this.credentialsFactory);

                        AuthenticateRequestResult authenticateRequestResult = new AuthenticateRequestResult();
                        authenticateRequestResult.setClaimsIdentity(claimsIdentity);
                        authenticateRequestResult.setAudience(outboundAudience);
                        authenticateRequestResult.setCallerId(resultCallerId);
                        authenticateRequestResult.setConnectorFactory(connectorFactory);

                        return CompletableFuture.completedFuture(authenticateRequestResult);
                    }
                );
            }
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<AuthenticateRequestResult> authenticateStreamingRequest(String authHeader, String channelIdHeader) {
        if (StringUtils.isNotBlank(channelIdHeader)) {
            this.credentialsFactory.isAuthenticationDisabled().thenCompose(isAuthDisabled -> {
                if (isAuthDisabled) {
                    return jwtTokenValidation_ValidateAuthHeader(authHeader, channelIdHeader, null).thenCompose(claimsIdentity -> {
                            String outboundAudience = SkillValidation.isSkillClaim(claimsIdentity.claims()) ?
                                JwtTokenValidation.getAppIdFromClaims(claimsIdentity.claims()) :
                                this.toChannelFromBotOAuthScope;

                            return generateCallerId(this.credentialsFactory, claimsIdentity, this.callerId).thenCompose(resultCallerId -> {
                                AuthenticateRequestResult authenticateRequestResult = new AuthenticateRequestResult();
                                authenticateRequestResult.setClaimsIdentity(claimsIdentity);
                                authenticateRequestResult.setAudience(outboundAudience);
                                authenticateRequestResult.setCallerId(resultCallerId);

                                return CompletableFuture.completedFuture(authenticateRequestResult);
                            });
                        }
                    );
                }
                return null;
            });
        }
        throw new AuthenticationException("channelId header required");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectorFactory createConnectorFactory(ClaimsIdentity claimsIdentity) {
        return new ConnectorFactoryImpl(
            getAppId(claimsIdentity),
            this.toChannelFromBotOAuthScope,
            this.toChannelFromBotLoginUrl,
            this.validateAuthority,
            this.credentialsFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<UserTokenClient> createUserTokenClient(ClaimsIdentity claimsIdentity) {
        String appId = getAppId(claimsIdentity);

        return this.credentialsFactory.createCredentials(
            appId,
            this.toChannelFromBotOAuthScope,
            this.toChannelFromBotLoginUrl,
            this.validateAuthority)
                .thenApply(credentials -> new UserTokenClientImpl(appId, credentials, this.oAuthUrl));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BotFrameworkClient createBotFrameworkClient() {
        return new BotFrameworkClientImpl(this.credentialsFactory, this.toChannelFromBotLoginUrl);
    }

    // The following code is based on JwtTokenValidation.AuthenticateRequest
    private CompletableFuture<ClaimsIdentity> jwtTokenValidation_AuthenticateRequest(Activity activity, String authHeader) {
        if (StringUtils.isBlank(authHeader)) {
            this.credentialsFactory.isAuthenticationDisabled().thenApply(isAuthDisabled -> {
                if (!isAuthDisabled) {
                    // No Auth Header. Auth is required. Request is not authorized.
                    throw new AuthenticationException("Unauthorized Access. Request is not authorized");
                }
                return null;
            });

            // Check if the activity is for a skill call and is coming from the Emulator.
            if (activity.getChannelId().equals(Channels.EMULATOR) && activity.getRecipient().getRole().equals(RoleTypes.SKILL)) {
                // Return an anonymous claim with an anonymous skill AppId
                return CompletableFuture.completedFuture(SkillValidation.createAnonymousSkillClaim());
            }

            // In the scenario where Auth is disabled, we still want to have the
            // IsAuthenticated flag set in the ClaimsIdentity. To do this requires
            // adding in an empty claim.
            return CompletableFuture.completedFuture(new ClaimsIdentity(AuthenticationConstants.ANONYMOUS_AUTH_TYPE));
        }

        // Validate the header and extract claims.
        return jwtTokenValidation_ValidateAuthHeader(authHeader, activity.getChannelId(), activity.getServiceUrl());
    }

    private CompletableFuture<ClaimsIdentity> jwtTokenValidation_ValidateAuthHeader(String authHeader, String channelId, String serviceUrl) {
        return jwtTokenValidation_AuthenticateToken(authHeader, channelId, serviceUrl)
            .thenCompose(identity -> jwtTokenValidation_ValidateClaims(identity.claims()).thenApply(result -> identity));
    }

    private CompletableFuture<Void> jwtTokenValidation_ValidateClaims(Map<String, String> claims) {
        if (this.authConfiguration.getClaimsValidator() != null) {
            // Call the validation method if defined (it should throw an exception if the validation fails)
            return this.authConfiguration.getClaimsValidator().validateClaims(claims);
        } else if (SkillValidation.isSkillClaim(claims)) {
            throw new AuthenticationException("ClaimsValidator is required for validation of Skill Host calls.");
        }
        return null;
    }

    private CompletableFuture<ClaimsIdentity> jwtTokenValidation_AuthenticateToken(String authHeader, String channelId, String serviceUrl) {
        if (SkillValidation.isSkillToken(authHeader)) {
            return this.skillValidation_AuthenticateChannelToken(authHeader, channelId);
        }

        if (EmulatorValidation.isTokenFromEmulator(authHeader)) {
            return this.emulatorValidation_AuthenticateEmulatorToken(authHeader, channelId);
        }

        return this.channelValidation_authenticateChannelToken(authHeader, serviceUrl, channelId);
    }

    // The following code is based on SkillValidation.AuthenticateChannelToken
    private CompletableFuture<ClaimsIdentity> skillValidation_AuthenticateChannelToken(String authHeader, String channelId) {
        TokenValidationParameters tokenValidationParameters = new TokenValidationParameters();
        tokenValidationParameters.validateIssuer = true;
        tokenValidationParameters.validIssuers = Arrays.asList(
            // TODO: presumably this table should also come from configuration
            "https://sts.windows.net/d6d49420-f39b-4df7-a1dc-d59a935871db/", // Auth v3.1, 1.0 token
            "https://login.microsoftonline.com/d6d49420-f39b-4df7-a1dc-d59a935871db/v2.0", // Auth v3.1, 2.0 token
            "https://sts.windows.net/f8cdef31-a31e-4b4a-93e4-5f571e91255a/", // Auth v3.2, 1.0 token
            "https://login.microsoftonline.com/f8cdef31-a31e-4b4a-93e4-5f571e91255a/v2.0", // Auth v3.2, 2.0 token
            "https://sts.windows.net/cab8a31a-1906-4287-a0d8-4eef66b95f6e/", // Auth for US Gov, 1.0 token
            "https://login.microsoftonline.us/cab8a31a-1906-4287-a0d8-4eef66b95f6e/v2.0" // Auth for US Gov, 2.0 token
        );
        tokenValidationParameters.validateAudience = false; // Audience validation takes place manually in code.
        tokenValidationParameters.validateLifetime = true;
        tokenValidationParameters.clockSkew = Duration.ofMinutes(5);
        tokenValidationParameters.requireSignedTokens = true;

        // TODO: what should the openIdMetadataUrl be here?
        JwtTokenExtractor tokenExtractor = new JwtTokenExtractor(
            tokenValidationParameters,
            this.toBotFromEmulatorOpenIdMetadataUrl,
            AuthenticationConstants.ALLOWED_SIGNING_ALGORITHMS);

        return tokenExtractor.getIdentity(authHeader, channelId, this.authConfiguration.requiredEndorsements()).thenCompose(
            identity -> skillValidation_ValidateIdentity(identity).thenApply(result -> identity)
        );
    }

    private CompletableFuture<Void> skillValidation_ValidateIdentity(ClaimsIdentity identity) {
        if (identity == null) {
            // No valid identity. Not Authorized.
            throw new AuthenticationException("SkillValidation.validateIdentity(): Invalid identity");
        }

        if (!identity.isAuthenticated()) {
            // The token is in some way invalid. Not Authorized.
            throw new AuthenticationException("SkillValidation.validateIdentity(): Token not authenticated");
        }

        String versionClaim = identity.getClaimValue(AuthenticationConstants.VERSION_CLAIM);

        if (versionClaim == null) {
            // No version claim
            throw new AuthenticationException(String.format("SkillValidation.validateIdentity(): '%s' claim is required on skill Tokens.", AuthenticationConstants.VERSION_CLAIM));
        }

        // Look for the "aud" claim, but only if issued from the Bot Framework
        String audienceClaim = identity.getClaimValue(AuthenticationConstants.AUDIENCE_CLAIM);
        if (StringUtils.isBlank(audienceClaim)) {
            // Claim is not present or doesn't have a value. Not Authorized.
            throw new AuthenticationException(String.format("SkillValidation.validateIdentity(): '%s' claim is required on skill Tokens.", AuthenticationConstants.AUDIENCE_CLAIM));
        }

        return this.credentialsFactory.isValidAppId(audienceClaim).thenCompose(result -> {
            if (!result) {
                // The AppId is not valid. Not Authorized.
                throw new AuthenticationException("SkillValidation.validateIdentity(): Invalid audience.");
            }

            String appId = JwtTokenValidation.getAppIdFromClaims(identity.claims());
            if (StringUtils.isBlank(appId)) {
                // Invalid appId
                throw new AuthenticationException("SkillValidation.validateIdentity(): Invalid appId.");
            }
            return null;
        });
    }

    // The following code is based on EmulatorValidation.AuthenticateEmulatorToken
    private CompletableFuture<ClaimsIdentity> emulatorValidation_AuthenticateEmulatorToken(String authHeader, String channelId) {
        TokenValidationParameters toBotFromEmulatorTokenValidationParameters = new TokenValidationParameters();
        toBotFromEmulatorTokenValidationParameters.validateIssuer = true;
        toBotFromEmulatorTokenValidationParameters.validIssuers = Arrays.asList(
            // TODO: presumably this table should also come from configuration
            "https://sts.windows.net/d6d49420-f39b-4df7-a1dc-d59a935871db/",                    // Auth v3.1, 1.0 token
            "https://login.microsoftonline.com/d6d49420-f39b-4df7-a1dc-d59a935871db/v2.0",      // Auth v3.1, 2.0 token
            "https://sts.windows.net/f8cdef31-a31e-4b4a-93e4-5f571e91255a/",                    // Auth v3.2, 1.0 token
            "https://login.microsoftonline.com/f8cdef31-a31e-4b4a-93e4-5f571e91255a/v2.0",      // Auth v3.2, 2.0 token
            "https://sts.windows.net/cab8a31a-1906-4287-a0d8-4eef66b95f6e/",                    // Auth for US Gov, 1.0 token
            "https://login.microsoftonline.us/cab8a31a-1906-4287-a0d8-4eef66b95f6e/v2.0"        // Auth for US Gov, 2.0 token
        );
        toBotFromEmulatorTokenValidationParameters.validateAudience = false; // Audience validation takes place manually in code.
        toBotFromEmulatorTokenValidationParameters.validateLifetime = true;
        toBotFromEmulatorTokenValidationParameters.clockSkew = Duration.ofMinutes(5);
        toBotFromEmulatorTokenValidationParameters.requireSignedTokens = true;

        JwtTokenExtractor tokenExtractor = new JwtTokenExtractor(
            toBotFromEmulatorTokenValidationParameters,
            this.toBotFromEmulatorOpenIdMetadataUrl,
            AuthenticationConstants.ALLOWED_SIGNING_ALGORITHMS);

        return tokenExtractor.getIdentity(authHeader, channelId, this.authConfiguration.requiredEndorsements()).thenCompose(identity -> {
            if (identity == null) {
                // No valid identity. Not Authorized.
                throw new AuthenticationException("Unauthorized. No valid identity.");
            }

            if (!identity.isAuthenticated()) {
                // The token is in some way invalid. Not Authorized.
                throw new AuthenticationException("Unauthorized. Is not authenticated");
            }

            // Now check that the AppID in the claimset matches
            // what we're looking for. Note that in a multi-tenant bot, this value
            // comes from developer code that may be reaching out to a service, hence the
            // Async validation.
            String versionClaim = identity.getClaimValue(AuthenticationConstants.VERSION_CLAIM);

            if (versionClaim == null) {
                throw new AuthenticationException("Unauthorized. 'ver' claim is required on Emulator Tokens.");
            }

            String tokenVersion = versionClaim;
            String appId = "";

            // The Emulator, depending on Version, sends the AppId via either the
            // appid claim (Version 1) or the Authorized Party claim (Version 2).
            if (StringUtils.isBlank(tokenVersion) || tokenVersion.equals("1.0")) {
                // either no Version or a version of "1.0" means we should look for
                // the claim in the "appid" claim.
                String appIdClaim = identity.getClaimValue(AuthenticationConstants.APPID_CLAIM);

                if (appIdClaim == null) {
                    // No claim around AppID. Not Authorized.
                    throw new AuthenticationException("Unauthorized. 'appid' claim is required on Emulator Token version '1.0'.");
                }

                appId = appIdClaim;
            } else if (tokenVersion.equals("2.0")) {
                // Emulator, "2.0" puts the AppId in the "azp" claim.
                String appZClaim = identity.getClaimValue(AuthenticationConstants.AUTHORIZED_PARTY);

                if (appZClaim == null) {
                    // No claim around AppID. Not Authorized.
                    throw new AuthenticationException("Unauthorized. 'azp' claim is required on Emulator Token version '2.0'.");
                }

                appId = appZClaim;
            } else {
                // Unknown Version. Not Authorized.
                throw new AuthenticationException(String.format("Unauthorized. Unknown Emulator Token version %s.", tokenVersion));
            }

            return this.credentialsFactory.isValidAppId(appId).thenApply(result -> {
                if (!result) {
                    throw new AuthenticationException("Unauthorized. Invalid AppId passed on token");
                }
                return identity;
            });
        });
    }

    // The following code is based on GovernmentChannelValidation.AuthenticateChannelToken
    private CompletableFuture<ClaimsIdentity> channelValidation_authenticateChannelToken(String authHeader, String serviceUrl, String channelId) {
        TokenValidationParameters tokenValidationParameters = this.channelValidation_GetTokenValidationParameters();

        JwtTokenExtractor tokenExtractor = new JwtTokenExtractor(
            tokenValidationParameters,
            this.toBotFromChannelOpenIdMetadataUrl,
            AuthenticationConstants.ALLOWED_SIGNING_ALGORITHMS);

        return tokenExtractor.getIdentity(authHeader, channelId, this.authConfiguration.requiredEndorsements())
            .thenCompose(identity -> governmentChannelValidation_ValidateIdentity(identity, serviceUrl)
                .thenApply(result -> identity));
    }

    private TokenValidationParameters channelValidation_GetTokenValidationParameters() {
        TokenValidationParameters tokenValidationParameters = new TokenValidationParameters();
        tokenValidationParameters.validateIssuer = true;
        tokenValidationParameters.validIssuers = Arrays.asList(this.toBotFromChannelTokenIssuer);

        // Audience validation takes place in JwtTokenExtractor
        tokenValidationParameters.validateAudience = false;
        tokenValidationParameters.validateLifetime = true;
        tokenValidationParameters.clockSkew = Duration.ofMinutes(5);
        tokenValidationParameters.requireSignedTokens = true;
        tokenValidationParameters.validateIssuerSigningKey = true;

        return tokenValidationParameters;
    }

    private CompletableFuture<Void> governmentChannelValidation_ValidateIdentity(ClaimsIdentity identity, String serviceUrl) {
        if (identity == null) {
            // No valid identity. Not Authorized.
            throw new AuthenticationException("Unauthorized. No valid identity.");
        }

        if (!identity.isAuthenticated()) {
            // The token is in some way invalid. Not Authorized.
            throw new AuthenticationException("Unauthorized. Is not authenticated");
        }

        // Now check that the AppID in the claimset matches
        // what we're looking for. Note that in a multi-tenant bot, this value
        // comes from developer code that may be reaching out to a service, hence the
        // async validation.

        // Look for the "aud" claim, but only if issued from the Bot Framework
        if (!identity.getClaimValue(AuthenticationConstants.ISSUER_CLAIM).equals(this.toBotFromChannelTokenIssuer)) {
            // The relevant Audiance Claim MUST be present. Not Authorized.
            throw new AuthenticationException("Unauthorized. Issuer Claim MUST be present.");
        }
        // The AppId from the claim in the token must match the AppId specified by the developer.
        // In this case, the token is destined for the app, so we find the app ID in the audience claim.
        String audienceClaim = identity.getClaimValue(AuthenticationConstants.AUDIENCE_CLAIM);
        if (StringUtils.isBlank(audienceClaim)) {
            // Claim is not present or is present, but doesn't have a value. Not Authorized.
            throw new AuthenticationException("Unauthorized. Issuer Claim MUST be present.");
        }

        return this.credentialsFactory.isValidAppId(audienceClaim).thenCompose(result -> {
            if (!result) {
                // The AppId is not valid. Not Authorized.
                throw new AuthenticationException("Invalid AppId passed on token: " + audienceClaim);
            }

            if (serviceUrl != null) {
                String serviceUrlClaim = identity.getClaimValue(AuthenticationConstants.SERVICE_URL_CLAIM);

                if (StringUtils.isBlank(serviceUrlClaim)) {
                    // Claim must be present. Not Authorized.
                    throw new AuthenticationException("Unauthorized. ServiceUrl claim should be present");
                }

                if (!serviceUrlClaim.equalsIgnoreCase(serviceUrlClaim)) {
                    // Claim must match. Not Authorized.
                    throw new AuthenticationException("Unauthorized. ServiceUrl claim do not match.");
                }
            }
            return null;
        });
    }

    private String getAppId(ClaimsIdentity claimsIdentity) {
        // For requests from channel App Id is in Audience claim of JWT token. For emulator it is in AppId claim. For
        // unauthenticated requests we have anonymous claimsIdentity provided auth is disabled.
        // For Activities coming from Emulator AppId claim contains the Bot's AAD AppId.
        String audienceClaim = claimsIdentity.getClaimValue(AuthenticationConstants.AUDIENCE_CLAIM);
        String appId = audienceClaim != null
            ? audienceClaim
            : claimsIdentity.getClaimValue(AuthenticationConstants.APPID_CLAIM);
        return appId;
    }
}

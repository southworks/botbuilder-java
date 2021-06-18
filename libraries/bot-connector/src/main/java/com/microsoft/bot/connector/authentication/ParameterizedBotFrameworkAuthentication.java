// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.connector.authentication;

import com.microsoft.bot.connector.Async;
import com.microsoft.bot.connector.Channels;
import com.microsoft.bot.connector.skills.BotFrameworkClient;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.RoleTypes;
import org.apache.commons.lang3.StringUtils;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
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
        validateAuthority = withValidateAuthority;
        toChannelFromBotLoginUrl = withToChannelFromBotLoginUrl;
        toChannelFromBotOAuthScope = withToChannelFromBotOAuthScope;
        toBotFromChannelTokenIssuer = withToBotFromChannelTokenIssuer;
        oAuthUrl = withOAuthUrl;
        toBotFromChannelOpenIdMetadataUrl = withToBotFromChannelOpenIdMetadataUrl;
        toBotFromEmulatorOpenIdMetadataUrl = withToBotFromEmulatorOpenIdMetadataUrl;
        callerId = withCallerId;
        credentialsFactory = withCredentialsFactory;
        authConfiguration = withAuthConfiguration;
    }

    @Override
    public String getOriginatingAudience() {
        return toChannelFromBotOAuthScope;
    }

    @Override
    public CompletableFuture<ClaimsIdentity> authenticateChannelRequest(String authHeader) {
        return jwtTokenValidation_ValidateAuthHeader(authHeader, "unknown", null);
    }

    @Override
    public CompletableFuture<AuthenticateRequestResult> authenticateRequest(Activity activity, String authHeader) {
        return jwtTokenValidation_AuthenticateRequest(activity, authHeader).thenCompose(claimsIdentity -> {
                String outboundAudience = SkillValidation.isSkillClaim(claimsIdentity.claims()) ?
                                          JwtTokenValidation.getAppIdFromClaims(claimsIdentity.claims()) :
                                          toChannelFromBotOAuthScope;

                generateCallerId(credentialsFactory, claimsIdentity, callerId).thenCompose(resultCallerId -> {
                        ConnectorFactoryImpl connectorFactory = new ConnectorFactoryImpl(
                                                             BuiltinBotFrameworkAuthentication.getAppId(claimsIdentity),
                                                             toChannelFromBotOAuthScope,
                                                             toChannelFromBotLoginUrl,
                                                             validateAuthority,
                                                             credentialsFactory);

                        AuthenticateRequestResult authenticateRequestResult = new AuthenticateRequestResult();
                        authenticateRequestResult.setClaimsIdentity(claimsIdentity);
                        authenticateRequestResult.setAudience(outboundAudience);
                        authenticateRequestResult.setCallerId(resultCallerId);
                        authenticateRequestResult.setConnectorFactory(connectorFactory);

                        return authenticateRequestResult;
                    }
                );
                return null;
            }
        );
    }

    @Override
    public CompletableFuture<AuthenticateRequestResult> authenticateStreamingRequest(String authHeader, String channelIdHeader) {
        credentialsFactory.isAuthenticationDisabled().thenCompose(result -> {
                if (StringUtils.isBlank(channelIdHeader) && !result) {
                    return Async.completeExceptionally(new RuntimeException());
                }
                return null;
            }
        );

        return jwtTokenValidation_ValidateAuthHeader(authHeader, channelIdHeader, null).thenCompose(claimsIdentity -> {
                String outboundAudience = SkillValidation.isSkillClaim(claimsIdentity.claims()) ?
                                          JwtTokenValidation.getAppIdFromClaims(claimsIdentity.claims()) :
                                          toChannelFromBotOAuthScope;

                generateCallerId(credentialsFactory, claimsIdentity, callerId).thenCompose(callerId -> {
                    AuthenticateRequestResult authenticateRequestResult = new AuthenticateRequestResult();
                    authenticateRequestResult.setClaimsIdentity(claimsIdentity);
                    authenticateRequestResult.setAudience(outboundAudience);
                    authenticateRequestResult.setCallerId(callerId);

                    return CompletableFuture.completedFuture(authenticateRequestResult);
                });
                return null;
            }
        );
    }

    @Override
    public ConnectorFactory createConnectorFactory(ClaimsIdentity claimsIdentity) {
        return new ConnectorFactoryImpl(
            BuiltinBotFrameworkAuthentication.getAppId(claimsIdentity),
            toChannelFromBotOAuthScope,
            toChannelFromBotLoginUrl,
            validateAuthority,
            credentialsFactory);
    }

    @Override
    public CompletableFuture<UserTokenClient> createUserTokenClient(ClaimsIdentity claimsIdentity) {
        String appId = BuiltinBotFrameworkAuthentication.getAppId(claimsIdentity);

        return credentialsFactory.createCredentials(appId, toChannelFromBotOAuthScope, toChannelFromBotLoginUrl, validateAuthority)
            .thenCompose(credentials -> new UserTokenClientImpl(appId, credentials, oAuthUrl));
    }

    @Override
    public BotFrameworkClient createBotFrameworkClient() {
        return new BotFrameworkClientImpl(credentialsFactory, toChannelFromBotLoginUrl);
    }

    // The following code is based on JwtTokenValidation.AuthenticateRequest
    private CompletableFuture<ClaimsIdentity> jwtTokenValidation_AuthenticateRequest(Activity activity, String authHeader) {
        if (StringUtils.isBlank(authHeader)) {
            credentialsFactory.isAuthenticationDisabled().thenCompose(isAuthDisabled -> {
                    if (!isAuthDisabled) {
                        // No Auth Header. Auth is required. Request is not authorized.
                        return Async.completeExceptionally(new RuntimeException());
                    }
                return null;
            });

            // Check if the activity is for a skill call and is coming from the Emulator.
            if (activity.getChannelId() == Channels.EMULATOR && activity.getRecipient().getRole() == RoleTypes.SKILL) {
                return CompletableFuture.completedFuture(SkillValidation.createAnonymousSkillClaim());
            }

            // In the scenario where Auth is disabled, we still want to have the
            // IsAuthenticated flag set in the ClaimsIdentity. To do this requires
            // adding in an empty claim.
            return CompletableFuture.completedFuture(new ClaimsIdentity(AuthenticationConstants.ANONYMOUS_AUTH_TYPE, new HashMap<String, String>()));
        }

        // Validate the header and extract claims.
        return jwtTokenValidation_ValidateAuthHeader(authHeader, activity.getChannelId(), activity.getServiceUrl())
            .thenApply(result -> { return null; });
    }

    private CompletableFuture<ClaimsIdentity> jwtTokenValidation_ValidateAuthHeader(String authHeader, String channelId, String serviceUrl) {
        return jwtTokenValidation_AuthenticateToken(authHeader, channelId, serviceUrl)
            .thenCompose(identity -> jwtTokenValidation_ValidateClaims(identity.claims()).thenApply(result -> identity));
    }

    private CompletableFuture<Void> jwtTokenValidation_ValidateClaims(Map<String, String> claims) {
        if (authConfiguration.getClaimsValidator() != null) {
            // Call the validation method if defined (it should throw an exception if the validation fails)
            return authConfiguration.getClaimsValidator().validateClaims(claims);
        } else if (SkillValidation.isSkillClaim(claims)) {
            return Async.completeExceptionally(new RuntimeException("ClaimsValidator is required for validation of Skill Host calls."));
        }
        return null;
    }

    private CompletableFuture<ClaimsIdentity> jwtTokenValidation_AuthenticateToken(String authHeader, String channelId, String serviceUrl) {
        if (SkillValidation.isSkillToken(authHeader)) {
            return skillValidation_AuthenticateChannelToken(authHeader, channelId).thenApply(result -> {return null;});
        }

        if (EmulatorValidation.isTokenFromEmulator(authHeader)) {
            return emulatorValidation_AuthenticateEmulatorToken(authHeader, channelId).thenApply(result -> {return null;});
        }

        return governmentChannelValidation_AuthenticateChannelToken(authHeader, serviceUrl, channelId).thenApply(result -> {return null;});
    }

    // The following code is based on SkillValidation.AuthenticateChannelToken
    private CompletableFuture<ClaimsIdentity> skillValidation_AuthenticateChannelToken(String authHeader, String channelId) {
        TokenValidationParameters tokenValidationParameters = new TokenValidationParameters();
        tokenValidationParameters.validateIssuer = true;
        tokenValidationParameters.validIssuers = Arrays.asList( // TODO: presumably this table should also come from configuration
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
                                                                toBotFromEmulatorOpenIdMetadataUrl,
                                                                AuthenticationConstants.ALLOWED_SIGNING_ALGORITHMS);

        tokenExtractor.getIdentity(authHeader, channelId, authConfiguration.requiredEndorsements()).thenCompose(
            identity -> skillValidation_ValidateIdentity(identity).thenApply(result -> identity)
        );
        return null;
    }

    private CompletableFuture<Void> skillValidation_ValidateIdentity(ClaimsIdentity identity) {
        if (identity == null) {
            // No valid identity. Not Authorized.
            return Async.completeExceptionally(new RuntimeException("Invalid Identity"));
        }

        if (!identity.isAuthenticated()) {
            // The token is in some way invalid. Not Authorized.
            return Async.completeExceptionally(new RuntimeException("Token Not Authenticated"));
        }

        String versionClaim = identity.claims().keySet().stream().filter
            (k -> k == AuthenticationConstants.VERSION_CLAIM).findFirst().orElse(null);

        if (versionClaim == null) {
            // No version claim
            return Async.completeExceptionally(new RuntimeException("'{AuthenticationConstants.VersionClaim}' claim is required on skill Tokens."));
        }

        // Look for the "aud" claim, but only if issued from the Bot Framework
        String audienceClaim = identity.claims().keySet().stream().filter
            (k -> k == AuthenticationConstants.AUDIENCE_CLAIM).findFirst().orElse(null);
        if (StringUtils.isBlank(audienceClaim)) {
            // Claim is not present or doesn't have a value. Not Authorized.
            return Async.completeExceptionally(new RuntimeException("'{AuthenticationConstants.AudienceClaim}' claim is required on skill Tokens."));
        }

        return credentialsFactory.isValidAppId(audienceClaim).thenCompose(result -> {
            if (!result) {
                // The AppId is not valid. Not Authorized.
                return Async.completeExceptionally(new RuntimeException("Invalid audience."));
            }
        });

        String appId = JwtTokenValidation.getAppIdFromClaims(identity.claims());
        if (StringUtils.isBlank(appId)) {
            // Invalid appId
            return Async.completeExceptionally(new RuntimeException("Invalid appId."));
        }
    }

    // The following code is based on EmulatorValidation.AuthenticateEmulatorToken
    private CompletableFuture<ClaimsIdentity> emulatorValidation_AuthenticateEmulatorToken(String authHeader, String channelId) {
        TokenValidationParameters toBotFromEmulatorTokenValidationParameters = new TokenValidationParameters();
        toBotFromEmulatorTokenValidationParameters.validateIssuer = true;
        toBotFromEmulatorTokenValidationParameters.validIssuers = Arrays.asList( // TODO: presumably this table should also come from configuration
            "https://sts.windows.net/d6d49420-f39b-4df7-a1dc-d59a935871db/",                    // Auth v3.1, 1.0 token
            "https://login.microsoftonline.com/d6d49420-f39b-4df7-a1dc-d59a935871db/v2.0",      // Auth v3.1, 2.0 token
            "https://sts.windows.net/f8cdef31-a31e-4b4a-93e4-5f571e91255a/",                    // Auth v3.2, 1.0 token
            "https://login.microsoftonline.com/f8cdef31-a31e-4b4a-93e4-5f571e91255a/v2.0",      // Auth v3.2, 2.0 token
            "https://sts.windows.net/cab8a31a-1906-4287-a0d8-4eef66b95f6e/",                    // Auth for US Gov, 1.0 token
            "https://login.microsoftonline.us/cab8a31a-1906-4287-a0d8-4eef66b95f6e/v2.0" // Auth for US Gov, 2.0 token
        );
        toBotFromEmulatorTokenValidationParameters.validateAudience = false; // Audience validation takes place manually in code.
        toBotFromEmulatorTokenValidationParameters.validateLifetime = true;
        toBotFromEmulatorTokenValidationParameters.clockSkew = Duration.ofMinutes(5);
        toBotFromEmulatorTokenValidationParameters.requireSignedTokens = true;

        JwtTokenExtractor tokenExtractor = new JwtTokenExtractor(
            toBotFromEmulatorTokenValidationParameters,
            toBotFromEmulatorOpenIdMetadataUrl,
            AuthenticationConstants.ALLOWED_SIGNING_ALGORITHMS);

        return tokenExtractor.getIdentity(authHeader, channelId, authConfiguration.requiredEndorsements()).thenCompose(identity -> {
            if (identity == null) {
                // No valid identity. Not Authorized.
                return Async.completeExceptionally(new RuntimeException("Invalid Identity"));
            }

            if (!identity.isAuthenticated()) {
                // The token is in some way invalid. Not Authorized.
                return Async.completeExceptionally(new RuntimeException("Token Not Authenticated"));
            }

            // Now check that the AppID in the claimset matches
            // what we're looking for. Note that in a multi-tenant bot, this value
            // comes from developer code that may be reaching out to a service, hence the
            // Async validation.
            String versionClaim = identity.claims().keySet().stream().filter
                (k -> k == AuthenticationConstants.VERSION_CLAIM).findFirst().orElse(null);

            if (versionClaim == null) {
                return Async.completeExceptionally(new RuntimeException("'ver' claim is required on Emulator Tokens."));
            }

            String tokenVersion = versionClaim;
            String appId = "";

            // The Emulator, depending on Version, sends the AppId via either the
            // appid claim (Version 1) or the Authorized Party claim (Version 2).
            if (StringUtils.isBlank(tokenVersion) || tokenVersion == "1.0") {
                // either no Version or a version of "1.0" means we should look for
                // the claim in the "appid" claim.
                String appIdClaim = identity.claims().keySet().stream().filter
                    (k -> k == AuthenticationConstants.APPID_CLAIM).findFirst().orElse(null);

                if (appIdClaim == null) {
                    // No claim around AppID. Not Authorized.
                    throw new RuntimeException("'appid' claim is required on Emulator Token version '1.0'.");
                }

                appId = appIdClaim;
            } else if (tokenVersion == "2.0") {
                // Emulator, "2.0" puts the AppId in the "azp" claim.
                String appZClaim = identity.claims().keySet().stream().filter
                    (k -> k == AuthenticationConstants.AUTHORIZED_PARTY).findFirst().orElse(null);

                if (appZClaim == null) {
                    // No claim around AppID. Not Authorized.
                    throw new RuntimeException("'azp' claim is required on Emulator Token version '2.0'.");
                }

                appId = appZClaim;
            } else {
                // Unknown Version. Not Authorized.
                throw new RuntimeException("Unknown Emulator Token version " + tokenVersion);
            }

            credentialsFactory.isValidAppId(appId).thenCompose(result -> {
                if (!result) {
                    return Async.completeExceptionally(new RuntimeException("Invalid AppId passed on token"));
                }
                return null;
            });

            return CompletableFuture.completedFuture(identity);
        });
    }

    // The following code is based on GovernmentChannelValidation.AuthenticateChannelToken

    private CompletableFuture<ClaimsIdentity> governmentChannelValidation_AuthenticateChannelToken(String authHeader, String serviceUrl, String channelId) {
        TokenValidationParameters tokenValidationParameters = governmentChannelValidation_GetTokenValidationParameters();

        JwtTokenExtractor tokenExtractor = new JwtTokenExtractor(
            tokenValidationParameters,
            toBotFromChannelOpenIdMetadataUrl,
            AuthenticationConstants.ALLOWED_SIGNING_ALGORITHMS);

        return tokenExtractor.getIdentity(authHeader, channelId, authConfiguration.requiredEndorsements()).thenCompose(identity -> {
            governmentChannelValidation_ValidateIdentity(identity, serviceUrl).thenApply(result -> null);

            return CompletableFuture.completedFuture(identity);
        });
    }

    private TokenValidationParameters governmentChannelValidation_GetTokenValidationParameters() {
        TokenValidationParameters tokenValidationParameters = new TokenValidationParameters();
        tokenValidationParameters.validateIssuer = true;
        tokenValidationParameters.validIssuers = Arrays.asList(toBotFromChannelTokenIssuer);
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
            throw new RuntimeException();
        }

        if (!identity.isAuthenticated()) {
            // The token is in some way invalid. Not Authorized.
            throw new RuntimeException();
        }

        // Now check that the AppID in the claimset matches
        // what we're looking for. Note that in a multi-tenant bot, this value
        // comes from developer code that may be reaching out to a service, hence the
        // async validation.

        // Look for the "aud" claim, but only if issued from the Bot Framework
        String audienceClaim = identity.claims().keySet().stream().filter
            (k -> k == AuthenticationConstants.AUDIENCE_CLAIM).findFirst().orElse(null);

        if (audienceClaim == null) {
            // The relevant audience Claim MUST be present. Not Authorized.
            throw new RuntimeException();
        }

        // The AppId from the claim in the token must match the AppId specified by the developer.
        // In this case, the token is destined for the app, so we find the app ID in the audience claim.
        if (StringUtils.isBlank(audienceClaim)) {
            // Claim is present, but doesn't have a value. Not Authorized.
            throw new RuntimeException();
        }

        credentialsFactory.isValidAppId(audienceClaim).thenCompose(result -> {
            if (!result) {
                // The AppId is not valid. Not Authorized.
                return Async.completeExceptionally(new RuntimeException("Invalid AppId passed on token: " + audienceClaim));
            }
            return null;
        });

        if (serviceUrl != null) {
            String serviceUrlClaim = identity.claims().keySet().stream().filter
                (k -> k == AuthenticationConstants.SERVICE_URL_CLAIM).findFirst().orElse(null);

            if (StringUtils.isBlank(serviceUrlClaim)) {
                // Claim must be present. Not Authorized.
                return Async.completeExceptionally(new RuntimeException());
            }

            if (!serviceUrlClaim.equalsIgnoreCase(serviceUrlClaim)) {
                // Claim must match. Not Authorized.
                return Async.completeExceptionally(new RuntimeException());
            }
        }
        return null;
    }
}

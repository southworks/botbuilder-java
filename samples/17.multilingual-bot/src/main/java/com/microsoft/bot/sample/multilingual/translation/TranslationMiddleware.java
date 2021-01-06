// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.multilingual.translation;

import java.awt.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import com.microsoft.bot.builder.Middleware;
import com.microsoft.bot.builder.NextDelegate;
import com.microsoft.bot.builder.StatePropertyAccessor;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.builder.UserState;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;

/**
 * Middleware for translating text between the user and bot.
 * Uses the Microsoft Translator Text API.
 */
public class TranslationMiddleware implements Middleware  {
    private MicrosoftTranslator translator;
    private StatePropertyAccessor<String> languageStateProperty;

    /**
     * Initializes a new instance of the {@link TranslationMiddleware} class.
     * @param withTranslator Translator implementation to be used for text translation.
     * @param userState State property for current language.
     */
    public TranslationMiddleware(MicrosoftTranslator withTranslator, UserState userState) {
        if (withTranslator == null) {
            throw new IllegalArgumentException("withTranslator");
        }
        this.translator = withTranslator;
        if (userState == null) {
            throw new IllegalArgumentException("userState");
        }

        this.languageStateProperty = userState.createProperty("LanguagePreference");
    }

    /**
     * Processes an incoming activity.
     * @param turnContext Context object containing information for a single turn of conversation with a user.
     * @param next The delegate to call to continue the bot middleware pipeline.
     * @return A Task representing the asynchronous operation.
     */
    public CompletableFuture<Void> onTurn(TurnContext turnContext, NextDelegate next) {
        if (turnContext == null) {
            throw new IllegalArgumentException("turnContext");
        }

        return this.shouldTranslate(turnContext).thenCompose(translate -> {
            if (translate) {
                if (turnContext.getActivity().getType() == ActivityTypes.MESSAGE) {
                    this.translator.translate(turnContext.getActivity().getText(), TranslationSettings.getDefaultLanguage()).thenApply(text -> {
                        turnContext.getActivity().setText(text);
                    });
                }

                return CompletableFuture.completedFuture(null);
            }
        }).thenCompose(task -> {
            turnContext.onSendActivities((newContext, activities, nextSend) -> {
                return this.languageStateProperty.get(turnContext, () -> TranslationSettings.getDefaultLanguage()).thenApply(userLanguage -> {
                    Boolean shouldTranslate = userLanguage != TranslationSettings.getDefaultLanguage();

                    // Translate messages sent to the user to user language
                    if (shouldTranslate) {
                        ArrayList<CompletableFuture<Void>> tasks = new ArrayList<CompletableFuture<Void>>();

                        for (Activity activity : activities.stream().filter(a -> a.getType() == ActivityTypes.MESSAGE)) {
                        //     tasks.add(this.translateMessageActivity(activity, userLanguage));
                        }

                    //     if (Arrays.asList(tasks).isEmpty()) {
                    //         CompletableFuture.allOf(tasks);
                    //     }

                        // return nextSend.get();
                    }
                });
            });

            // turnContext.onUpdateActivity((newContext, activity, nextUpdate) -> {
            //     return this.languageStateProperty.get(turnContext, () -> TranslationSettings.getDefaultLanguage()).thenApply(userLanguage -> {
            //         Boolean shouldTranslate = userLanguage != TranslationSettings.getDefaultLanguage();

            //         // Translate messages sent to the user to user language
            //         if (activity.getType() == ActivityTypes.MESSAGE) {
            //             if (shouldTranslate) {
            //                 this.translateMessageActivity(activity, targetLocale);
            //             }
            //         }


            //         return nextUpdate.get();
            //     });
            // });
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletableFuture<Void> translateMessageActivity(Activity activity, String targetLocale) {
        if (activity.getType() == ActivityTypes.MESSAGE) {
            activity.setText(this.translator.translate(activity.getText(), targetLocale));
        }
    }

    private CompletableFuture<Boolean> shouldTranslate(TurnContext turnContext) {
        return this.languageStateProperty.get(turnContext, () -> TranslationSettings.getDefaultLanguage()).thenApply(userLanguage -> {
            if (userLanguage == null) {
                userLanguage = TranslationSettings.getDefaultLanguage();
            }
            return userLanguage != TranslationSettings.getDefaultLanguage();
        });
    }
}

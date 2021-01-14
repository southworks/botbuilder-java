// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.ai.qna;

import com.microsoft.bot.ai.qna.dialogs.QnAMakerDialog;

/**
 * Class which contains registration of components for QnAMaker.
 */
// TODO: This class needs the missing ComponentRegistration class and
// IComponentDeclarativeTypes interface
public class QnAMakerComponentRegistration extends ComponentRegistration implements IComponentDeclarativeTypes {
    /**
     * Gets declarative type registrations for QnAMAker.
     * @param resourceExplorer resourceExplorer to use for resolving references.
     * @return enumeration of DeclarativeTypes.
     */
    public DeclarativeType[] getDeclarativeTypes(ResourceExplorer resourceExplorer) {
        DeclarativeType[] declarativeTypes = {
            // Dialogs
            new DeclarativeType(){
                setKind(QnAMakerDialog.getKind());
                setType(QnAMakerDialog.getClass().getName());
            },
            // Recognizers
            new DeclarativeType(){
                setKind(QnAMakerRecognizer.getKind());
                setType(QnAMakerRecognizer.getClass().getName());
            }
        };

        return declarativeTypes;
    }
}

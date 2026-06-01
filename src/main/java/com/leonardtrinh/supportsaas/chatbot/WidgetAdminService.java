package com.leonardtrinh.supportsaas.chatbot;

import java.util.UUID;

public interface WidgetAdminService {

    WidgetResponse getWidget();

    WidgetResponse createWidget();

    WidgetResponse updateConfig(UUID id, UpdateWidgetConfigRequest request);

    WidgetResponse replaceKnowledgeBases(UUID id, ReplaceKnowledgeBasesRequest request);
}

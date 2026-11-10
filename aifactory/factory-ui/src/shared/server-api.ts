/* tslint:disable */
/* eslint-disable */

export interface ModelListRequest {
    provider?: string | null;
    url?: string | null;
    agent?: string | null;
    agentUrl?: string | null;
    refresh?: boolean | null;
}

export interface AgentSettingsDto {
    id: string;
    name: string;
    overridden: boolean;
    model?: string | null;
    url?: string | null;
    think?: string | null;
    temperature?: string | null;
    extraBody?: string | null;
    tools?: string | null;
    keySet: boolean;
}

export interface AgentSettingsUpdateRequest {
    overridden?: boolean | null;
    model?: string | null;
    url?: string | null;
    think?: string | null;
    temperature?: string | null;
    extraBody?: string | null;
    tools?: string | null;
    apiKey?: string | null;
    removeApiKey?: boolean | null;
}

export interface ProviderDefaultsDto {
    name: string;
    defaultUrl?: string | null;
    thinkSupported: boolean;
    thinkForm: ThinkForm;
    thinkValues: string[];
    extraBodySupported: boolean;
}

export interface SettingsDto {
    provider: string;
    model?: string | null;
    think?: string | null;
    url?: string | null;
    tokenWindow: number;
    timeoutSeconds: number;
    sendThinking: boolean;
    maxTokens?: number | null;
    queryParams?: string | null;
    headerParams?: string | null;
    keySet: boolean;
    keySource: KeySource;
}

export interface SettingsUpdateRequest {
    provider?: string | null;
    model?: string | null;
    url?: string | null;
    apiKey?: string | null;
    tokenWindow?: string | null;
    timeoutSeconds?: string | null;
    sendThinking?: string | null;
    think?: string | null;
    maxTokens?: string | null;
    queryParams?: string | null;
    headerParams?: string | null;
    removeApiKey?: boolean | null;
}

export interface AddProjectRequest {
    gitUrl: string;
}

export interface ProjectDto {
    id: string;
    name: string;
    gitUrl: string;
    localPath: string;
    state: string;
}

export interface ProjectWorkDto {
    projectId: string;
    working: boolean;
    queued: number;
}

export interface ApiError {
    status: number;
    error: string;
    message: string;
    fieldErrors?: ApiFieldError[] | null;
}

export interface ApiFieldError {
    field: string;
    message: string;
}

export type ThinkForm = "TOGGLE" | "VALUES" | "FREE" | "NONE" | "UNKNOWN";

export type KeySource = "FILE" | "ENV" | "NONE";

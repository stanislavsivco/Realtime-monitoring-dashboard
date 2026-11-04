import { HttpErrorResponse } from "@angular/common/http";

export function getErrorMessage(err: HttpErrorResponse): string {
    if (err.status === 0) {
        return 'Server is not reachable. Check your connection and try again.';
    }
    if (err.status === 400) {
        return 'The server rejected the data. Check the values and try again.';
    }
    if (err.status === 401) {
        return 'Your session has expired. Sign in again';
    }
    if (err.status === 403) {
        return 'Your do not have permission to do this.'
    }
    if (err.status === 404) {
        return 'The item was not found. It may have been deleted.';
    }
    if (err.status === 409) {
        return getServerMessage(err, 'This item conflicts with existing data.');
    }
    if (err.status === 429) {
        return 'Too many requests. Try again later.';
    }
    if (err.status >= 500) {
        return 'Something went wrong on the server. Try again later.';
    }
    return 'Something went wrong. Try again.';
}

function getServerMessage(err: HttpErrorResponse, fallback: string): string {
    if (err.error && typeof err.error.message === 'string') {
        return err.error.message;
    }
    return fallback;
}
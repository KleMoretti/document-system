package internal

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestWriteJSONEncodesNilSlicesAsEmptyArrays(t *testing.T) {
	recorder := httptest.NewRecorder()

	writeJSON(recorder, http.StatusOK, []Document(nil))

	if recorder.Body.String() != "[]\n" {
		t.Fatalf("expected empty array JSON, got %q", recorder.Body.String())
	}
}

func TestDecodeRejectsOversizedBodies(t *testing.T) {
	recorder := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodPost, "/api/documents", strings.NewReader(`{"body":"`+strings.Repeat("x", maxRequestBodyBytes)+`"}`))

	var target map[string]string
	if decode(recorder, request, &target) {
		t.Fatal("expected oversized body to be rejected")
	}
	if recorder.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("expected 413, got %d", recorder.Code)
	}
}

package internal

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestWriteJSONEncodesNilSlicesAsEmptyArrays(t *testing.T) {
	recorder := httptest.NewRecorder()

	writeJSON(recorder, http.StatusOK, []Document(nil))

	if recorder.Body.String() != "[]\n" {
		t.Fatalf("expected empty array JSON, got %q", recorder.Body.String())
	}
}

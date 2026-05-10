package internal

import "testing"

func TestCanEditOnlyOwnerAndEditor(t *testing.T) {
	if !CanEdit("owner") {
		t.Fatal("owner should edit")
	}
	if !CanEdit("editor") {
		t.Fatal("editor should edit")
	}
	if CanEdit("viewer") {
		t.Fatal("viewer should not edit")
	}
}

func TestCanShareOnlyOwner(t *testing.T) {
	if !CanShare("owner") {
		t.Fatal("owner should share")
	}
	if CanShare("editor") {
		t.Fatal("editor should not share")
	}
	if CanShare("viewer") {
		t.Fatal("viewer should not share")
	}
}

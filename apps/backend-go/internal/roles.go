package internal

func CanEdit(role string) bool {
	return role == "owner" || role == "editor"
}

func CanShare(role string) bool {
	return role == "owner"
}

func ValidRole(role string) bool {
	return role == "owner" || role == "editor" || role == "viewer"
}

export type User = {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
};

export type DocumentSummary = {
  id: string;
  title: string;
  ownerId: string;
  role: 'owner' | 'editor' | 'viewer';
  createdAt: string;
  updatedAt: string;
};

export type Share = {
  userId: string;
  email: string;
  displayName: string;
  role: 'owner' | 'editor' | 'viewer';
};

export type AuthResponse = {
  token: string;
  user: User;
};

export type ApiError = {
  code: string;
  message: string;
};

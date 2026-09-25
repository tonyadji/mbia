import { Outlet } from 'react-router';

export function RootLayout() {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-5xl flex-col px-4 py-6 sm:px-8">
      <Outlet />
    </main>
  );
}

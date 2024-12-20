export default function RootLayout({ children }) {
  return (
    <html lang="en" >
      <body className="container">
        <main>{children}</main>
      </body>
    </html>
  );
}

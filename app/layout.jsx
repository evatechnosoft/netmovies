export default function RootLayout({ children }) {
  return (
    <html lang="en" className={interFontFamily.className}>
      <body className="container">
        <Header />
        <main>{children}</main>
        <Footer />
      </body>
    </html>
  );
}

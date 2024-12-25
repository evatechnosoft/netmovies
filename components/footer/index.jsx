import React from 'react';
import styles from './styles.module.css';
import Link from 'next/link';

function Footer() {
  return (
    <footer className={styles.footer}>
      Made for &nbsp;
      <Link href="https://google.com" target="_blank">
        Eralp
      </Link>
    </footer>
  );
}
export default Footer;

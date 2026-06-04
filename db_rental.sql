-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: localhost
-- Generation Time: Jun 03, 2026 at 04:14 PM
-- Server version: 10.4.28-MariaDB
-- PHP Version: 8.2.4

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `db_rental`
--

-- --------------------------------------------------------

--
-- Table structure for table `member`
--

CREATE TABLE `member` (
  `id_member` varchar(10) NOT NULL,
  `nama` varchar(50) DEFAULT NULL,
  `jenis` varchar(10) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `member`
--

INSERT INTO `member` (`id_member`, `nama`, `jenis`) VALUES
('M01', 'Anton', 'Regular');

-- --------------------------------------------------------

--
-- Table structure for table `playstation`
--

CREATE TABLE `playstation` (
  `id_ps` varchar(10) NOT NULL,
  `tipe` varchar(10) DEFAULT NULL,
  `harga_per_jam` double DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `playstation`
--

INSERT INTO `playstation` (`id_ps`, `tipe`, `harga_per_jam`, `status`) VALUES
('PS4-01', 'PS4', 5000, 'Tersedia'),
('PS4-02', 'PS4', 5000, 'Tersedia'),
('PS4-03', 'PS4', 5000, 'Tersedia'),
('PS5-01', 'PS5', 10000, 'Tersedia'),
('PS5-02', 'PS5', 10000, 'Tersedia');

-- --------------------------------------------------------

--
-- Table structure for table `transaksi`
--

CREATE TABLE `transaksi` (
  `id_transaksi` int(11) NOT NULL,
  `id_member` varchar(10) DEFAULT NULL,
  `nama_guest` varchar(50) DEFAULT NULL,
  `id_ps` varchar(10) DEFAULT NULL,
  `waktu_mulai` datetime DEFAULT NULL,
  `durasi_jam` int(11) DEFAULT NULL,
  `total_biaya` double DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `transaksi`
--

INSERT INTO `transaksi` (`id_transaksi`, `id_member`, `nama_guest`, `id_ps`, `waktu_mulai`, `durasi_jam`, `total_biaya`, `status`) VALUES
(1, NULL, 'Safix', 'PS4-01', '2026-06-01 23:09:11', 5, 25000, 'Selesai'),
(2, 'M01', NULL, 'PS4-03', '2026-06-01 23:10:38', 2, 10000, 'Selesai'),
(3, 'M01', NULL, 'PS5-01', '2026-06-01 23:10:45', 2, 25000, 'Selesai'),
(4, 'M01', NULL, 'PS4-02', '2026-06-01 23:16:24', 2, 10000, 'Selesai'),
(5, 'M01', NULL, 'PS5-01', '2026-06-01 23:26:03', 3, 35000, 'Selesai'),
(6, 'M01', NULL, 'PS5-02', '2026-06-01 23:26:21', 8, 85000, 'Selesai'),
(7, NULL, 'Ariel', 'PS4-02', '2026-06-02 22:39:18', 2, 10000, 'Selesai');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `member`
--
ALTER TABLE `member`
  ADD PRIMARY KEY (`id_member`);

--
-- Indexes for table `playstation`
--
ALTER TABLE `playstation`
  ADD PRIMARY KEY (`id_ps`);

--
-- Indexes for table `transaksi`
--
ALTER TABLE `transaksi`
  ADD PRIMARY KEY (`id_transaksi`),
  ADD KEY `id_member` (`id_member`),
  ADD KEY `id_ps` (`id_ps`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `transaksi`
--
ALTER TABLE `transaksi`
  MODIFY `id_transaksi` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `transaksi`
--
ALTER TABLE `transaksi`
  ADD CONSTRAINT `transaksi_ibfk_1` FOREIGN KEY (`id_member`) REFERENCES `member` (`id_member`) ON DELETE SET NULL,
  ADD CONSTRAINT `transaksi_ibfk_2` FOREIGN KEY (`id_ps`) REFERENCES `playstation` (`id_ps`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;

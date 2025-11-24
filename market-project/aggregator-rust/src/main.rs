use std::thread;
use std::time::Duration;
use rand::Rng;
use serde::{Deserialize, Serialize};
use reqwest::blocking::Client;
#[derive(Deserialize, Debug)]
struct BinanceTicker {
    symbol: String,
    price: String,
}
#[derive(Serialize)]
struct PriceData {
    symbol: String,
    price: f64,
    source: String,
    timestamp: u64,
    is_anomaly: bool,
}
fn main() {
    let gateway_url = "http://java-gateway:8080/api/ingest";
    let binance_url = "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT";
    let client = Client::new();
    let mut rng = rand::thread_rng();

    println!("Rust Aggregator LIVE: Conectare la Binance API...");

    loop {
        let current_price: f64;
        let source_label: String;

        match client.get(binance_url).send() {
            Ok(resp) => {
                if let Ok(ticker) = resp.json::<BinanceTicker>() {
                    current_price = ticker.price.parse().unwrap_or(0.0);
                    source_label = String::from("Binance-API");
                    println!("🌍 Live from Binance: ${:.2}", current_price);
                } else {
                    current_price = rng.gen_range(90000.0..92000.0);
                    source_label = String::from("Backup-Generator");
                    println!("⚠️ Eroare JSON Binance. Folosesc Backup.");
                }
            },
            Err(e) => {
                current_price = rng.gen_range(90000.0..92000.0);
                source_label = String::from("Backup-Generator");
                println!("⚠️ Eroare rețea ({}). Folosesc Backup.", e);
            }
        }

        let anomaly = current_price > 98000.0 || current_price < 80000.0;

        let price_packet = PriceData {
            symbol: String::from("BTC-USD"),
            price: current_price,
            source: source_label,
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs(),
            is_anomaly: anomaly,
        };

        match client.post(gateway_url).json(&price_packet).send() {
            Ok(_) => {},
            Err(e) => eprintln!("❌ Java Gateway e jos: {}", e),
        }
        thread::sleep(Duration::from_secs(3));
    }
}
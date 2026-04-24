use std::collections::HashMap;

fn word_counts(text: &str) -> HashMap<String, usize> {
    let mut counts = HashMap::new();
    for token in text.split_whitespace() {
        let key = token.to_ascii_lowercase();
        *counts.entry(key).or_insert(0) += 1;
    }
    counts
}

fn top_n(counts: &HashMap<String, usize>, n: usize) -> Vec<(String, usize)> {
    let mut pairs: Vec<_> = counts.iter().map(|(k, v)| (k.clone(), *v)).collect();
    pairs.sort_by(|a, b| b.1.cmp(&a.1));
    pairs.truncate(n);
    pairs
}

fn main() {
    let text = "the quick brown fox jumps over the lazy dog the end";
    let counts = word_counts(text);
    for (word, n) in top_n(&counts, 3) {
        println!("{}: {}", word, n);
    }
}

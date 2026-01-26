pub mod network {
    pub struct Server {
        address: String,
        port: u16,
    }

    impl Server {
        pub fn new(address: String, port: u16) -> Self {
            Server { address, port }
        }

        pub fn start(&self) {
            self.bind();
            self.listen();
        }

        fn bind(&self) {
            println!("Binding to {}:{}", self.address, self.port);
        }

        fn listen(&self) {
            println!("Listening for connections");
        }
    }

    pub(crate) fn internal_helper() {
        println!("Internal helper function");
    }
}

pub struct Config {
    pub host: String,
    pub port: u16,
    debug: bool,
}

impl Config {
    pub fn new(host: String, port: u16) -> Self {
        Config {
            host,
            port,
            debug: false,
        }
    }

    pub fn enable_debug(&mut self) {
        self.debug = true;
    }

    pub(crate) fn is_debug(&self) -> bool {
        self.debug
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_helper() {
        let config = Config::new(String::from("localhost"), 8080);
        assert_eq!(config.port, 8080);
    }
}

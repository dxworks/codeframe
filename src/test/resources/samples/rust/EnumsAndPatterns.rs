pub enum Message {
    Quit,
    Move { x: i32, y: i32 },
    Write(String),
    ChangeColor(i32, i32, i32),
}

impl Message {
    pub fn call(&self) {
        match self {
            Message::Quit => println!("Quit message"),
            Message::Move { x, y } => println!("Move to ({}, {})", x, y),
            Message::Write(text) => println!("Write: {}", text),
            Message::ChangeColor(r, g, b) => println!("Color: ({}, {}, {})", r, g, b),
        }
    }
}

pub enum Result<T, E> {
    Ok(T),
    Err(E),
}

impl<T, E> Result<T, E> {
    pub fn is_ok(&self) -> bool {
        matches!(self, Result::Ok(_))
    }

    pub fn is_err(&self) -> bool {
        matches!(self, Result::Err(_))
    }

    pub fn unwrap(self) -> T
    where
        E: std::fmt::Debug,
    {
        match self {
            Result::Ok(val) => val,
            Result::Err(err) => panic!("called `Result::unwrap()` on an `Err` value: {:?}", err),
        }
    }
}

pub fn process_message(msg: Message) -> String {
    match msg {
        Message::Quit => String::from("Quitting"),
        Message::Move { x, y } if x > 0 && y > 0 => format!("Moving to positive quadrant: ({}, {})", x, y),
        Message::Move { x, y } => format!("Moving to ({}, {})", x, y),
        Message::Write(text) => text,
        Message::ChangeColor(r, g, b) => format!("RGB({}, {}, {})", r, g, b),
    }
}

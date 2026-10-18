import React, { useState, useEffect, useRef } from 'react';
import { api } from '../api/client';
import { Case } from '../types/api';
import { Search, X } from 'lucide-react';

interface Props {
  selectedCases: Case[];
  onChange: (cases: Case[]) => void;
}

export const SearchableCaseSelect: React.FC<Props> = ({ selectedCases, onChange }) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Case[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [wrapperRef]);

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    
    const fetchCases = async () => {
      setLoading(true);
      try {
        const res: any = await api.request(`/api/v1/cases?search=${encodeURIComponent(query)}&size=5`);
        setResults(res.content || []);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    
    const timeout = setTimeout(fetchCases, 300);
    return () => clearTimeout(timeout);
  }, [query]);

  const handleSelect = (c: Case) => {
    if (!selectedCases.find(sc => sc.id === c.id)) {
      onChange([...selectedCases, c]);
    }
    setQuery('');
    setOpen(false);
  };

  const handleRemove = (id: string) => {
    onChange(selectedCases.filter(sc => sc.id !== id));
  };

  return (
    <div className="relative" ref={wrapperRef}>
      <div className="flex flex-wrap gap-2 mb-2">
        {selectedCases.map(c => (
          <div key={c.id} className="flex items-center gap-1 px-2 py-1 bg-jf-violet/10 text-jf-violet rounded-md text-sm font-medium">
            {c.caseNumber}
            <button type="button" onClick={() => handleRemove(c.id)} className="hover:text-jf-orange">
              <X className="w-3 h-3" />
            </button>
          </div>
        ))}
      </div>
      
      <div className="relative">
        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <Search className="h-4 w-4 text-jf-ink-muted" />
        </div>
        <input
          type="text"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          placeholder="Search by case number..."
          className="w-full pl-9 pr-3 py-2 border border-jf-border-subtle rounded-lg focus:ring-2 focus:ring-jf-violet text-sm"
        />
      </div>

      {open && query.length >= 2 && (
        <div className="absolute z-10 w-full mt-1 bg-white border border-jf-border-subtle rounded-lg shadow-lg max-h-60 overflow-y-auto">
          {loading ? (
            <div className="p-3 text-sm text-jf-ink-muted">Searching...</div>
          ) : results.length > 0 ? (
            <ul className="py-1">
              {results.map((c) => (
                <li
                  key={c.id}
                  onClick={() => handleSelect(c)}
                  className="px-3 py-2 hover:bg-jf-canvas cursor-pointer text-sm"
                >
                  <span className="font-semibold">{c.caseNumber}</span> - {c.caseType}
                </li>
              ))}
            </ul>
          ) : (
            <div className="p-3 text-sm text-jf-ink-muted">No cases found</div>
          )}
        </div>
      )}
    </div>
  );
};
